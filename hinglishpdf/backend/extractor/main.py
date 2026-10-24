import statistics
from typing import List
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
import fitz  # PyMuPDF

app = FastAPI(title="HinglishPDF Extractor Service", version="1.0.0")

class BlockInfo(BaseModel):
    text: str
    font_size: float
    bbox: List[float]
    is_heading: bool
    is_header_footer: bool = False

class PageInfo(BaseModel):
    page_number: int
    page_width: float = 0.0
    page_height: float = 0.0
    total_chars: int
    blocks: List[BlockInfo]

class ExtractionResult(BaseModel):
    page_count: int
    total_chars: int
    avg_chars_per_page: float
    pages: List[PageInfo]

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "extractor"}

def merge_line_spans(spans):
    """
    Merges spans in a line while respecting horizontal spacing.
    Prevents artificial spaces in small-caps or formatted text (e.g., 'K ALEIDOSCOPE' -> 'KALEIDOSCOPE').
    """
    if not spans:
        return "", []
    
    line_text = ""
    span_sizes = []
    prev_span = None
    
    for s in spans:
        stext = s.get("text", "")
        if not stext:
            continue
        
        span_sizes.append(s.get("size", 11.0))
        
        if prev_span is None:
            line_text = stext
        else:
            prev_ends_space = line_text.endswith(" ")
            curr_starts_space = stext.startswith(" ")
            
            if prev_ends_space or curr_starts_space:
                if prev_ends_space and curr_starts_space:
                    line_text += stext.lstrip()
                else:
                    line_text += stext
            else:
                prev_x1 = prev_span["bbox"][2]
                curr_x0 = s["bbox"][0]
                gap = curr_x0 - prev_x1
                font_sz = min(prev_span.get("size", 10.0), s.get("size", 10.0))
                # If gap is at least ~22% of font size or 1.8pt, it is a word boundary
                space_thresh = max(1.8, font_sz * 0.22)
                if gap >= space_thresh:
                    line_text += " " + stext
                else:
                    line_text += stext
        prev_span = s
        
    return line_text.strip(), span_sizes

def deduplicate_block_lines(lines_info):
    """
    Deduplicates lines within a block:
    - Filters out drop-shadow fragments (e.g. 'I S' when 'I Sell my Dreams' is present at the same vertical baseline).
    - Collapses consecutive duplicate lines (e.g. repeated 'Stop and Think').
    - Collapses repeated digits/characters (e.g. '11111' -> '1').
    """
    # 1. Filter out drop-shadow fragments
    filtered = []
    for i, info in enumerate(lines_info):
        lt = info["text"]
        ym = info["y_mid"]
        is_shadow = False
        for j, other in enumerate(lines_info):
            if i != j and abs(ym - other["y_mid"]) < 6.0:
                if lt != other["text"] and lt in other["text"]:
                    is_shadow = True
                    break
        if not is_shadow:
            filtered.append(info)

    # 2. Collapse consecutive identical lines and character repetitions
    collapsed = []
    for info in filtered:
        t = info["text"]
        if len(t) >= 3 and len(set(t)) == 1 and t[0].isalnum():
            t = t[0]
        if not collapsed or collapsed[-1] != t:
            collapsed.append(t)

    return collapsed

@app.post("/extract", response_model=ExtractionResult)
async def extract_pdf(file: UploadFile = File(...)):
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are supported.")

    try:
        pdf_bytes = await file.read()
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to parse PDF: {str(e)}")

    page_count = len(doc)
    if page_count == 0:
        doc.close()
        raise HTTPException(status_code=400, detail="PDF contains 0 pages.")

    if page_count > 100:
        doc.close()
        raise HTTPException(status_code=400, detail=f"PDF has {page_count} pages. Maximum allowed is 100.")

    pages_info: List[PageInfo] = []
    doc_font_sizes: List[float] = []

    # First pass: collect span font sizes to identify common body font size
    for page in doc:
        page_dict = page.get_text("dict")
        for block in page_dict.get("blocks", []):
            if block.get("type") == 0:  # Text block
                for line in block.get("lines", []):
                    for span in line.get("spans", []):
                        text = span.get("text", "").strip()
                        if text:
                            doc_font_sizes.append(span.get("size", 11.0))

    body_font_size = statistics.median(doc_font_sizes) if doc_font_sizes else 11.0
    total_chars_doc = 0

    # Second pass: construct clean blocks with heading detection and shadow removal
    for page_idx, page in enumerate(doc):
        page_dict = page.get_text("dict")
        page_blocks: List[BlockInfo] = []
        page_char_count = 0
        p_width = round(page.rect.width, 2)
        p_height = round(page.rect.height, 2)

        for block in page_dict.get("blocks", []):
            if block.get("type") == 0:  # Text block
                lines_info = []
                all_span_sizes = []

                for line in block.get("lines", []):
                    line_text, span_sizes = merge_line_spans(line.get("spans", []))
                    if line_text:
                        bbox = line.get("bbox", [0.0, 0.0, 0.0, 0.0])
                        y_mid = (bbox[1] + bbox[3]) / 2.0
                        lines_info.append({
                            "text": line_text,
                            "bbox": bbox,
                            "y_mid": y_mid
                        })
                        all_span_sizes.extend(span_sizes)

                if not lines_info:
                    continue

                clean_lines = deduplicate_block_lines(lines_info)
                full_block_text = "\n".join(clean_lines).strip()
                if not full_block_text:
                    continue

                avg_block_font_size = (
                    sum(all_span_sizes) / len(all_span_sizes) if all_span_sizes else body_font_size
                )
                bbox = [round(v, 2) for v in block.get("bbox", [0.0, 0.0, 0.0, 0.0])]

                # Header/footer detection based on margin geometry
                is_header_footer = (bbox[3] <= p_height * 0.13) or (bbox[1] >= p_height * 0.90)

                # Heading heuristic: font noticeably larger than median and short text (< 150 chars)
                is_heading = (
                    avg_block_font_size >= (body_font_size * 1.2)
                    and len(full_block_text) <= 150
                    and not full_block_text.endswith(".")
                    and not is_header_footer
                )

                page_char_count += len(full_block_text)
                page_blocks.append(
                    BlockInfo(
                        text=full_block_text,
                        font_size=round(avg_block_font_size, 2),
                        bbox=bbox,
                        is_heading=is_heading,
                        is_header_footer=is_header_footer,
                    )
                )

        # Sort blocks in natural visual reading order: top-to-bottom (grouped by vertical bands of ~8pt), left-to-right
        page_blocks.sort(key=lambda b: (round(b.bbox[1] / 8.0), b.bbox[0]))

        total_chars_doc += page_char_count
        pages_info.append(
            PageInfo(
                page_number=page_idx + 1,
                page_width=p_width,
                page_height=p_height,
                total_chars=page_char_count,
                blocks=page_blocks,
            )
        )

    doc.close()

    avg_chars = round(total_chars_doc / page_count, 2) if page_count > 0 else 0.0

    return ExtractionResult(
        page_count=page_count,
        total_chars=total_chars_doc,
        avg_chars_per_page=avg_chars,
        pages=pages_info,
    )
