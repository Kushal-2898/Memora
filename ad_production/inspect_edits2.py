import json
from pathlib import Path

transcript_path = Path(r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl')

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            step = json.loads(line)
            idx = step.get('step_index')
            if 23215 <= idx <= 23250:
                calls = step.get('tool_calls', [])
                for tc in calls:
                    fn = tc.get('name')
                    args = tc.get('args', {})
                    if 'edit_file' in fn or 'write_file' in fn:
                        print(f"Step {idx} [{fn}]: {args.get('Path')}")
                        target = args.get('Target', '')[:100].replace('\n', ' ')
                        replacement = args.get('Replacement', '')[:100].replace('\n', ' ')
                        if target:
                            print(f"  Target: {target}")
                            print(f"  Repl  : {replacement}")
        except:
            pass
