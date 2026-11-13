import json

transcript_path = r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl'

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        step = json.loads(line)
        idx = step.get('step_index')
        if idx in [23780, 23781, 23782, 23783, 23784]:
            print(f"=== STEP {idx} ===")
            print(step.get('content'))
            print()
