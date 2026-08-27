# Project Constraints

## Language Policy

This project enforces a strict **English-only** policy for all code-related content.

### Rules

1. **No Chinese characters in source code**
   - All source files (`.java`, `.ts`, `.vue`, `.js`, `.xml`, `.yaml`, `.properties`, `.sql`, etc.) must not contain any Chinese characters.
   - This includes string literals, comments, variable names, function names, class names, and configuration values.

2. **English comments only**
   - All comments must be written in English.
   - Do not leave Chinese comments, even temporarily.

3. **English naming conventions**
   - Variables, functions, classes, interfaces, enums, constants, and packages must use English names.
   - Use clear, descriptive English names. Avoid abbreviations unless they are universally understood.

4. **English in logs and error messages**
   - All log output and exception/error messages must be in English.

5. **English in documentation inside code**
   - Javadoc, JSDoc, docstrings, and inline documentation must be in English.

### Exceptions

- External dependency names and third-party API keys that inherently contain non-ASCII characters are allowed.
- Files explicitly designated as localization resources (e.g., `messages_zh.properties`) are exempt, but their keys should still be in English.
- This `AGENTS.md` file itself is exempt for readability during instruction.
- **Test scripts, utility scripts, and standalone producer scripts** (e.g., Kafka/MQTT test producers, one-off data generators) may use Chinese comments and console output for ease of local maintenance. This exception applies only to scripts that are not part of the main application source code.

### Enforcement

When generating or modifying code for this project:
- If the user prompt is in Chinese, respond in Chinese but **output code in English**.
- Translate any intended Chinese text into equivalent English before inserting into the codebase.
- If a file contains Chinese content, refactor it to English before making other changes.

## Communication and Documentation Policy

### Rules

1. **Code is English, communication is Chinese**
   - All source code, comments, logs, and error messages must be in English (as per Language Policy above).
   - All replies, explanations, and discussions with the user should be in **Chinese** for readability.
   - This includes: task summaries, error analysis, design explanations, and any conversational output.

2. **Documentation in Chinese**
   - Project documentation files (`.md`, `.txt`, reports, design docs) should be written in **Chinese** unless explicitly requested otherwise.
   - Technical terms, class names, method names, and code references can remain in English within Chinese text.
   - Example: "在 `AlarmProcessFunction` 的 `map()` 方法中，阈值评估逻辑如下..."

3. **Code blocks remain English**
   - All code snippets, SQL statements, configuration examples in documentation must be in English.
   - Inline code references (e.g., `PointAlarmState`, `alarm:latest`) stay in English even within Chinese sentences.

### Summary

| Content Type | Language |
|-------------|----------|
| Source code (.java, .ts, .vue, etc.) | English |
| Code comments | English |
| Log messages | English |
| Error messages | English |
| Replies to user | Chinese |
| Documentation (.md reports) | Chinese |
| Code blocks in docs | English |
| Technical terms in Chinese text | English (inline) |

## Git Commit Policy

### Rules

1. **Ask before committing**
   - At the end of a session, if any files were modified (i.e., there are git changes), a commit may be made.
   - However, **always ask the user first** whether the changes should be committed. Never commit without explicit confirmation for that batch of changes.

2. **Local commits only — never push**
   - Only commit to the **local** repository (`git commit`).
   - **Never push to any remote** (`git push` is forbidden unless the user explicitly asks for a push in that specific instance).

3. **Only commit what was asked**
   - Stage only the files related to the task performed in the session. Do not sweep unrelated working-tree changes into the commit.
