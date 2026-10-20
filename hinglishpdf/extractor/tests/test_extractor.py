import io
import fitz
from fastapi.testclient import TestClient
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from main import app

client = TestClient(app)

def create_sample_pdf(title: str, body: str) -> bytes:
    doc = fitz.open()
    page = doc.new_page()
    # Insert heading with larger font
    page.insert_text((50, 72), title, fontsize=18)
    # Insert body with regular font
    page.insert_text((50, 120), body, fontsize=11)
    pdf_bytes = doc.tobytes()
    doc.close()
    return pdf_bytes

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok", "service": "extractor"}

def test_extract_valid_pdf():
    pdf_bytes = create_sample_pdf(
        "Machine Learning Overview",
        "Machine learning is a subset of artificial intelligence that involves training algorithms."
    )
    files = {"file": ("test.pdf", pdf_bytes, "application/pdf")}
    res = client.post("/extract", files=files)
    assert res.status_code == 200
    data = res.json()

    assert data["page_count"] == 1
    assert data["total_chars"] > 50
    assert len(data["pages"]) == 1

    page = data["pages"][0]
    assert page["page_number"] == 1
    assert len(page["blocks"]) >= 2

    # Check heading detection
    heading_blocks = [b for b in page["blocks"] if b["is_heading"]]
    assert len(heading_blocks) >= 1
    assert "Machine Learning Overview" in heading_blocks[0]["text"]
    assert heading_blocks[0]["font_size"] >= 14.0

def test_reject_non_pdf():
    files = {"file": ("test.txt", b"plain text", "text/plain")}
    res = client.post("/extract", files=files)
    assert res.status_code == 400
    assert "Only PDF files are supported" in res.json()["detail"]

if __name__ == "__main__":
    test_health()
    test_extract_valid_pdf()
    test_reject_non_pdf()
    print("All extractor tests passed successfully!")
