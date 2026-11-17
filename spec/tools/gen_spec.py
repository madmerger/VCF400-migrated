"""日本語仕様書を固定した原典抽出データから再生成する。"""
import json
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill

ROOT = Path(__file__).resolve().parents[2]


def write_book(path, sheets):
    book = Workbook()
    book.remove(book.active)
    for title, rows in sheets.items():
        sheet = book.create_sheet(title)
        for row in rows:
            sheet.append(row)
        sheet.freeze_panes = 'B2'
        sheet.auto_filter.ref = sheet.dimensions
        for cell in sheet[1]:
            cell.fill = PatternFill('solid', fgColor='234E70')
            cell.font = Font(color='FFFFFF', bold=True)
        for row in sheet:
            for cell in row:
                cell.alignment = Alignment(vertical='top', wrap_text=True)
        for col in sheet.columns:
            sheet.column_dimensions[col[0].column_letter].width = 18 if col[0].column == 1 else 48
        for row in range(2, sheet.max_row + 1):
            sheet.row_dimensions[row].height = 62
    path.parent.mkdir(parents=True, exist_ok=True)
    book.save(path)


if __name__ == '__main__':
    data = json.loads((Path(__file__).with_name('spec_data.json')).read_text())
    write_book(ROOT / 'spec/VCF400_spec.xlsx', data)
    print('仕様書生成: spec/VCF400_spec.xlsx')
