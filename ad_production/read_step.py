import json
from pathlib import Path

transcript_path = Path(r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl')

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            step = json.loads(line)
            idx = step.get('step_index')
            if idx in [23184, 23185, 23186, 23187, 23188, 23189, 23190]:
                print(f"=== STEP {idx} ({step.get('type')}) ===")
                print(step.get('content')[:1000])
                print()
        except Exception as e:
            pass
