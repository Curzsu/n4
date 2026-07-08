"""
Generate alarm rule configuration Excel from points-2.log.

Extracts all unique point paths and creates a structured template
for configuring alarm thresholds, severities, and messages.
"""

import re
import json
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation


# ── Config ──────────────────────────────────────────────────────────
LOG_FILE = Path(__file__).parent.parent / "材料" / "points-2.log"
OUTPUT_FILE = Path(__file__).parent.parent / "材料" / "alarm_rules_config.xlsx"

THRESHOLD_LEVELS = ["Normal", "High", "High-High", "Low", "Low-Low", "Offline"]
SEVERITY_OPTIONS = ["Critical", "Major", "Minor", "Warning"]

# Color scheme for threshold level headers
LEVEL_COLORS = {
    "Normal":    "4CAF50",  # green
    "High":      "FF9800",  # orange
    "High-High": "F44336",  # red
    "Low":       "2196F3",  # blue
    "Low-Low":   "9C27B0",  # purple
    "Offline":   "607D8B",  # blue-grey
}


# ── Parse log ───────────────────────────────────────────────────────
def extract_unique_paths(log_path: Path) -> list[str]:
    pattern = re.compile(r'timestamp=\[([^\]]+)\]\s*,topic=([^,]+)\s*,key=(.*?)\s*,value=(.*)')
    paths = set()
    with open(log_path, "r", encoding="utf-8") as f:
        for line in f:
            m = pattern.match(line.strip())
            if m:
                paths.add(m.group(3).strip())
    return sorted(paths)


# ── Build Excel ─────────────────────────────────────────────────────
def build_workbook(paths: list[str]) -> Workbook:
    wb = Workbook()
    ws = wb.active
    ws.title = "Alarm Rules"

    thin_border = Border(
        left=Side(style="thin", color="B0B0B0"),
        right=Side(style="thin", color="B0B0B0"),
        top=Side(style="thin", color="B0B0B0"),
        bottom=Side(style="thin", color="B0B0B0"),
    )

    # ── Row 1: Group headers (threshold level names, merged) ────────
    ws.merge_cells("A1:A2")
    ws["A1"] = "path"
    ws["A1"].font = Font(bold=True, size=11, color="FFFFFF")
    ws["A1"].fill = PatternFill("solid", fgColor="333333")
    ws["A1"].alignment = Alignment(horizontal="center", vertical="center")
    ws["A1"].border = thin_border

    col = 2
    for level in THRESHOLD_LEVELS:
        color = LEVEL_COLORS[level]
        start_col = get_column_letter(col)
        end_col = get_column_letter(col + 2)
        ws.merge_cells(f"{start_col}1:{end_col}1")
        cell = ws[f"{start_col}1"]
        cell.value = level
        cell.font = Font(bold=True, size=11, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor=color)
        cell.alignment = Alignment(horizontal="center")
        cell.border = thin_border
        # Also fill the merged area cells with border
        for c in range(col, col + 3):
            ws.cell(row=1, column=c).border = thin_border
        col += 3

    # ── Row 2: Sub-headers (Value / Severity / Message) ─────────────
    sub_headers = ["Value", "Severity", "Message"]
    col = 2
    for level in THRESHOLD_LEVELS:
        color = LEVEL_COLORS[level]
        for sub in sub_headers:
            cell = ws.cell(row=2, column=col, value=sub)
            cell.font = Font(bold=True, size=10, color="FFFFFF")
            cell.fill = PatternFill("solid", fgColor=color)
            cell.alignment = Alignment(horizontal="center")
            cell.border = thin_border
            col += 1

    # ── Data rows ───────────────────────────────────────────────────
    for i, path in enumerate(paths, start=3):
        row = i
        # Column A: path
        cell = ws.cell(row=row, column=1, value=path)
        cell.font = Font(size=9)
        cell.alignment = Alignment(vertical="center")
        cell.border = thin_border

        col = 2
        for level in THRESHOLD_LEVELS:
            # Value column
            val_cell = ws.cell(row=row, column=col)
            val_cell.border = thin_border
            val_cell.alignment = Alignment(horizontal="center", vertical="center")
            if level == "Normal":
                val_cell.value = None
                val_cell.fill = PatternFill("solid", fgColor="E8F5E9")
            elif level == "Offline":
                val_cell.value = "Null"
                val_cell.fill = PatternFill("solid", fgColor="ECEFF1")
            else:
                val_cell.fill = PatternFill("solid", fgColor="FFF8E1")

            # Severity column
            sev_cell = ws.cell(row=row, column=col + 1)
            sev_cell.border = thin_border
            sev_cell.alignment = Alignment(horizontal="center", vertical="center")
            if level == "Normal":
                sev_cell.fill = PatternFill("solid", fgColor="E8F5E9")
            elif level == "Offline":
                sev_cell.fill = PatternFill("solid", fgColor="ECEFF1")
            else:
                sev_cell.fill = PatternFill("solid", fgColor="FFF8E1")

            # Message column
            msg_cell = ws.cell(row=row, column=col + 2)
            msg_cell.border = thin_border
            msg_cell.alignment = Alignment(vertical="center")
            if level == "Normal":
                msg_cell.fill = PatternFill("solid", fgColor="E8F5E9")
            elif level == "Offline":
                msg_cell.fill = PatternFill("solid", fgColor="ECEFF1")
            else:
                msg_cell.fill = PatternFill("solid", fgColor="FFF8E1")

            col += 3

    # ── Data validation: Severity dropdown ──────────────────────────
    severity_formula = '"' + ",".join(SEVERITY_OPTIONS) + '"'
    dv = DataValidation(type="list", formula1=severity_formula, allow_blank=True)
    dv.error = "Please select: Critical, Major, Minor, or Warning"
    dv.errorTitle = "Invalid Severity"
    dv.prompt = "Select alarm severity"
    dv.promptTitle = "Severity"

    # Apply to all severity columns (columns 3, 6, 9, 12, 15, 18)
    last_row = len(paths) + 2
    for col_idx in [3, 6, 9, 12, 15, 18]:
        col_letter = get_column_letter(col_idx)
        dv.add(f"{col_letter}3:{col_letter}{last_row}")
    ws.add_data_validation(dv)

    # ── Column widths ───────────────────────────────────────────────
    ws.column_dimensions["A"].width = 70  # path
    col = 2
    for level in THRESHOLD_LEVELS:
        ws.column_dimensions[get_column_letter(col)].width = 12      # Value
        ws.column_dimensions[get_column_letter(col + 1)].width = 12  # Severity
        ws.column_dimensions[get_column_letter(col + 2)].width = 40  # Message
        col += 3

    # ── Freeze panes ────────────────────────────────────────────────
    ws.freeze_panes = "B3"

    # ── Add a legend/instructions sheet ─────────────────────────────
    ws2 = wb.create_sheet("Instructions")
    instructions = [
        ["Alarm Rules Configuration Guide"],
        [""],
        ["Column", "Description"],
        ["path", "Point path (unique identifier for each device point)"],
        ["Value (Normal)", "Leave empty — Normal is the default state"],
        ["Value (High)", "Upper threshold — alarm triggers when value >= this"],
        ["Value (High-High)", "Critical upper threshold — alarm triggers when value >= this"],
        ["Value (Low)", "Lower threshold — alarm triggers when value <= this"],
        ["Value (Low-Low)", "Critical lower threshold — alarm triggers when value <= this"],
        ["Value (Offline)", "Fixed as 'Null' — triggers when point stops reporting"],
        ["Severity", "Alarm level: Critical / Major / Minor / Warning (dropdown)"],
        ["Message", "Custom alarm message text"],
        [""],
        ["Severity Levels"],
        ["Critical", "System-threatening condition, immediate action required"],
        ["Major", "Significant deviation, attention needed soon"],
        ["Minor", "Slight deviation, monitor closely"],
        ["Warning", "Advisory, no immediate risk"],
        [""],
        ["Color Legend"],
        ["Green background", "Normal — no value/severity needed"],
        ["Yellow background", "User-configurable threshold fields"],
        ["Grey background", "Offline — value fixed as 'Null'"],
    ]
    for r, row_data in enumerate(instructions, start=1):
        for c, val in enumerate(row_data, start=1):
            cell = ws2.cell(row=r, column=c, value=val)
            if r == 1:
                cell.font = Font(bold=True, size=14)
            elif r in (3, 14, 20):
                cell.font = Font(bold=True, size=11)
            else:
                cell.font = Font(size=10)

    ws2.column_dimensions["A"].width = 25
    ws2.column_dimensions["B"].width = 70

    return wb


# ── Main ────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print(f"Reading log file: {LOG_FILE}")
    paths = extract_unique_paths(LOG_FILE)
    print(f"Found {len(paths)} unique points")

    wb = build_workbook(paths)
    wb.save(str(OUTPUT_FILE))
    print(f"Saved to: {OUTPUT_FILE}")
