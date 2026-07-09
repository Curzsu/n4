"""
Generate alarm rule configuration Excel from points-2.log.

Only boolean and numeric points are kept; other value types are filtered out.
Columns:
  - path
  - Boolean:  Value / Severity / Message
  - High:     Value / Severity / Message / Hysteresis
  - High-High:Value / Severity / Message / Hysteresis
  - Low:      Value / Severity / Message / Hysteresis
  - Low-Low:  Value / Severity / Message / Hysteresis
"""

import re
import json
import sys
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation


# ═══════════════════════════════════════════════════════════════════════
# Config
# ═══════════════════════════════════════════════════════════════════════

LOG_FILE = Path(__file__).parent.parent / "材料" / "points-2.log"
OUTPUT_FILE = Path(__file__).parent.parent / "材料" / "alarm_rules_config.xlsx"

SEVERITY_OPTIONS = ["Critical", "Major", "Minor", "Warning"]

# Level metadata: name, sub-column headers, header color
LEVELS = [
    {"name": "Boolean",   "cols": ["Value", "Severity", "Message"],             "color": "4CAF50"},
    {"name": "High",      "cols": ["Value", "Severity", "Message", "Hysteresis"], "color": "FF9800"},
    {"name": "High-High", "cols": ["Value", "Severity", "Message", "Hysteresis"], "color": "F44336"},
    {"name": "Low",       "cols": ["Value", "Severity", "Message", "Hysteresis"], "color": "2196F3"},
    {"name": "Low-Low",   "cols": ["Value", "Severity", "Message", "Hysteresis"], "color": "9C27B0"},
]

# ═══════════════════════════════════════════════════════════════════════
# Parse log
# ═══════════════════════════════════════════════════════════════════════

def classify_value(value) -> str | None:
    """Classify a raw point value as 'boolean' or 'numeric'. Return None if neither."""
    if value is None:
        return None
    s = str(value).strip().lower()
    if s in ("true", "false"):
        return "boolean"
    try:
        float(s)
        return "numeric"
    except (ValueError, TypeError):
        return None


def parse_log_line(line: str) -> dict | None:
    """Parse a single log line and return the value JSON object."""
    pattern = re.compile(
        r'timestamp=\[([^\]]+)\]\s*,topic=([^,]+)\s*,key=(.*?)\s*,value=(.*)'
    )
    m = pattern.match(line.strip())
    if not m:
        return None
    value_str = m.group(4).strip()
    try:
        return json.loads(value_str)
    except json.JSONDecodeError:
        return None


def extract_valid_points(log_path: Path) -> dict[str, dict]:
    """
    Read the log file and keep the latest JSON template for each unique path.
    Only retain points whose value is boolean or numeric.
    Returns: {path: value_json_with_point_type}
    """
    points: dict[str, dict] = {}
    valid_count = 0
    skipped_count = 0

    with open(log_path, "r", encoding="utf-8") as f:
        for line in f:
            value_json = parse_log_line(line)
            if not value_json:
                continue

            path = value_json.get("path")
            if not path:
                continue

            point_type = classify_value(value_json.get("value"))
            if point_type is None:
                skipped_count += 1
                continue

            value_json["_point_type"] = point_type
            points[path] = value_json
            valid_count += 1

    print(f"解析有效记录: {valid_count} 条，保留点位: {len(points)} 个，跳过非布尔/数值: {skipped_count} 条")
    return points


# ═══════════════════════════════════════════════════════════════════════
# Build Excel
# ═══════════════════════════════════════════════════════════════════════

def build_workbook(points: dict[str, dict]) -> Workbook:
    wb = Workbook()
    ws = wb.active
    ws.title = "Alarm Rules"

    thin_border = Border(
        left=Side(style="thin", color="B0B0B0"),
        right=Side(style="thin", color="B0B0B0"),
        top=Side(style="thin", color="B0B0B0"),
        bottom=Side(style="thin", color="B0B0B0"),
    )

    fill_header = PatternFill("solid", fgColor="333333")
    fill_editable = PatternFill("solid", fgColor="FFF8E1")

    # ── Row 1: Group headers (merged level names) ─────────────────────
    ws.merge_cells("A1:A2")
    cell = ws["A1"]
    cell.value = "path"
    cell.font = Font(bold=True, size=11, color="FFFFFF")
    cell.fill = fill_header
    cell.alignment = Alignment(horizontal="center", vertical="center")
    cell.border = thin_border

    col = 2
    for level in LEVELS:
        color = level["color"]
        col_count = len(level["cols"])
        start_col = get_column_letter(col)
        end_col = get_column_letter(col + col_count - 1)
        ws.merge_cells(f"{start_col}1:{end_col}1")

        cell = ws[f"{start_col}1"]
        cell.value = level["name"]
        cell.font = Font(bold=True, size=11, color="FFFFFF")
        cell.fill = PatternFill("solid", fgColor=color)
        cell.alignment = Alignment(horizontal="center", vertical="center")
        cell.border = thin_border

        for c in range(col, col + col_count):
            ws.cell(row=1, column=c).border = thin_border

        col += col_count

    # ── Row 2: Sub-headers ────────────────────────────────────────────
    col = 2
    for level in LEVELS:
        color = level["color"]
        for sub in level["cols"]:
            cell = ws.cell(row=2, column=col, value=sub)
            cell.font = Font(bold=True, size=10, color="FFFFFF")
            cell.fill = PatternFill("solid", fgColor=color)
            cell.alignment = Alignment(horizontal="center", vertical="center")
            cell.border = thin_border
            col += 1

    # ── Data rows ─────────────────────────────────────────────────────
    sorted_paths = sorted(points.keys())
    for i, path in enumerate(sorted_paths, start=3):
        row = i

        # Column A: path
        cell = ws.cell(row=row, column=1, value=path)
        cell.font = Font(size=9)
        cell.alignment = Alignment(vertical="center")
        cell.border = thin_border

        # Remaining columns: all editable; hysteresis defaults to 0.00
        col = 2
        for level in LEVELS:
            for sub in level["cols"]:
                cell = ws.cell(row=row, column=col)
                cell.border = thin_border
                cell.alignment = Alignment(horizontal="center", vertical="center")
                cell.fill = fill_editable
                if sub == "Hysteresis":
                    cell.value = 0.00
                col += 1

    # ── Data validation ───────────────────────────────────────────────
    last_row = len(sorted_paths) + 2

    # Severity dropdown (all severity columns)
    severity_cols = []
    col = 2
    for level in LEVELS:
        for idx, sub in enumerate(level["cols"]):
            if sub == "Severity":
                severity_cols.append(col + idx)
        col += len(level["cols"])

    severity_formula = '"' + ",".join(SEVERITY_OPTIONS) + '"'
    dv_severity = DataValidation(
        type="list", formula1=severity_formula, allow_blank=True
    )
    dv_severity.error = "Please select: Critical, Major, Minor, or Warning"
    dv_severity.errorTitle = "Invalid Severity"
    dv_severity.prompt = "Select alarm severity"
    dv_severity.promptTitle = "Severity"
    for col_idx in severity_cols:
        col_letter = get_column_letter(col_idx)
        dv_severity.add(f"{col_letter}3:{col_letter}{last_row}")
    ws.add_data_validation(dv_severity)

    # Boolean value dropdown
    dv_bool = DataValidation(
        type="list", formula1='"TRUE,FALSE"', allow_blank=True
    )
    dv_bool.error = "Please select TRUE or FALSE"
    dv_bool.errorTitle = "Invalid Boolean Value"
    dv_bool.prompt = "Select boolean alarm value"
    dv_bool.promptTitle = "Boolean Value"
    bool_value_col = 2  # Boolean Value is column B
    col_letter = get_column_letter(bool_value_col)
    dv_bool.add(f"{col_letter}3:{col_letter}{last_row}")
    ws.add_data_validation(dv_bool)

    # Numeric value validation (High / High-High / Low / Low-Low Value columns)
    numeric_value_cols = []
    col = 2
    for level in LEVELS:
        if level["name"] != "Boolean":
            numeric_value_cols.append(col)  # first sub-column is Value
        col += len(level["cols"])

    dv_numeric = DataValidation(type="decimal", allow_blank=True)
    dv_numeric.error = "Please enter a valid number"
    dv_numeric.errorTitle = "Invalid Number"
    for col_idx in numeric_value_cols:
        col_letter = get_column_letter(col_idx)
        dv_numeric.add(f"{col_letter}3:{col_letter}{last_row}")
    ws.add_data_validation(dv_numeric)

    # Hysteresis validation (non-negative decimal)
    hysteresis_cols = []
    col = 2
    for level in LEVELS:
        for idx, sub in enumerate(level["cols"]):
            if sub == "Hysteresis":
                hysteresis_cols.append(col + idx)
        col += len(level["cols"])

    dv_hysteresis = DataValidation(
        type="decimal", operator="greaterThanOrEqual", formula1="0", allow_blank=True
    )
    dv_hysteresis.error = "Hysteresis must be a non-negative number"
    dv_hysteresis.errorTitle = "Invalid Hysteresis"
    dv_hysteresis.prompt = "Enter deadband value (e.g. 0.20)"
    dv_hysteresis.promptTitle = "Hysteresis"
    for col_idx in hysteresis_cols:
        col_letter = get_column_letter(col_idx)
        dv_hysteresis.add(f"{col_letter}3:{col_letter}{last_row}")
    ws.add_data_validation(dv_hysteresis)

    # ── Column widths ─────────────────────────────────────────────────
    ws.column_dimensions["A"].width = 70
    col = 2
    for level in LEVELS:
        for sub in level["cols"]:
            if sub == "Message":
                ws.column_dimensions[get_column_letter(col)].width = 40
            elif sub == "Hysteresis":
                ws.column_dimensions[get_column_letter(col)].width = 14
            else:
                ws.column_dimensions[get_column_letter(col)].width = 12
            col += 1

    # ── Freeze panes ──────────────────────────────────────────────────
    ws.freeze_panes = "B3"

    # ── Instructions sheet ────────────────────────────────────────────
    ws2 = wb.create_sheet("Instructions")
    instructions = [
        ["Alarm Rules Configuration Guide"],
        [""],
        ["Column", "Description"],
        ["path", "Point path (unique identifier for each device point)"],
        ["Value (Boolean)", "Alarm trigger value: TRUE or FALSE (dropdown)"],
        ["Severity (Boolean)", "Alarm severity: Critical / Major / Minor / Warning"],
        ["Message (Boolean)", "Custom alarm message text"],
        ["Value (High)", "Upper threshold — alarm triggers when value >= this"],
        ["Hysteresis (High)", "Deadband added above the threshold (normal range becomes value <= threshold + hysteresis)"],
        ["Value (High-High)", "Critical upper threshold"],
        ["Hysteresis (High-High)", "Deadband added above the critical threshold"],
        ["Value (Low)", "Lower threshold — alarm triggers when value <= this"],
        ["Hysteresis (Low)", "Deadband subtracted below the threshold (normal range becomes value >= threshold - hysteresis)"],
        ["Value (Low-Low)", "Critical lower threshold"],
        ["Hysteresis (Low-Low)", "Deadband subtracted below the critical threshold"],
        [""],
        ["Mutual Exclusion Rule"],
        ["Boolean point", "Fill only the Boolean section. High / High-High / Low / Low-Low must be empty."],
        ["Numeric point", "Leave the Boolean section empty. Fill High / High-High / Low / Low-Low as needed."],
        ["Auto detection", "Backend determines the point type by which section is configured."],
        ["Validation", "If both Boolean and numeric sections are filled, the backend will reject the conflicting configuration."],
        [""],
        ["Severity Levels"],
        ["Critical", "System-threatening condition, immediate action required"],
        ["Major", "Significant deviation, attention needed soon"],
        ["Minor", "Slight deviation, monitor closely"],
        ["Warning", "Advisory, no immediate risk"],
        [""],
        ["Note"],
        ["All fields are editable in Excel", "The backend will determine point type and enforce mutual exclusion when loading the rules."],
    ]
    for r, row_data in enumerate(instructions, start=1):
        for c, val in enumerate(row_data, start=1):
            cell = ws2.cell(row=r, column=c, value=val)
            if r == 1:
                cell.font = Font(bold=True, size=14)
            elif r in (3, 18, 25, 31):
                cell.font = Font(bold=True, size=11)
            else:
                cell.font = Font(size=10)

    ws2.column_dimensions["A"].width = 28
    ws2.column_dimensions["B"].width = 90

    return wb


# ═══════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print(f"Reading log file: {LOG_FILE}")
    points = extract_valid_points(LOG_FILE)
    print(f"Found {len(points)} valid points (boolean or numeric)")

    if not points:
        print("No valid points found. Exiting.")
        sys.exit(1)

    wb = build_workbook(points)
    wb.save(str(OUTPUT_FILE))
    print(f"Saved to: {OUTPUT_FILE}")
