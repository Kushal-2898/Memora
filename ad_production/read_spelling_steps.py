import json

transcript_path = r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl'

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        step = json.loads(line)
        idx = step.get('step_index')
        if 23730 <= idx <= 23784:
            content = str(step.get('content', ''))
            if 'ai' in content.lower():
                print(f"=== STEP {idx} ===")
                for l in content.splitlines():
                    if 'ai' in l.lower() or 'found' in l.lower() or 'spell' in l.lower() or 'remove' in l.lower():
                        print("  ", l[:120])
