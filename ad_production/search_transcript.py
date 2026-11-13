import json
from pathlib import Path

transcript_path = Path(r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl')
if not transcript_path.exists():
    print("Transcript not found")
    exit()

print("Searching transcript for user requests mentioning AI...")
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            step = json.loads(line)
            content = str(step.get('content', ''))
            if step.get('source') == 'USER_EXPLICIT' or step.get('type') == 'USER_INPUT':
                if any(w in content.lower() for w in ['remove', 'offline', 'gemini', 'cloud', 'ai']):
                    print(f"Step {step.get('step_index')}: {content[:180]}")
        except Exception as e:
            pass
