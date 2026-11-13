import json
from pathlib import Path

transcript_path = Path(r'C:\Users\meruv\.gemini\antigravity\brain\0dd08cc1-7011-4819-a6fd-077b0073e304\.system_generated\logs\transcript.jsonl')

with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            step = json.loads(line)
            idx = step.get('step_index')
            if idx >= 23186:
                content = str(step.get('content', ''))
                # If content mentions AI
                if 'ai' in content.lower():
                    source = step.get('source', '')
                    stype = step.get('type', '')
                    # Check if it was an assistant response or user message
                    if stype in ['PLANNER_RESPONSE', 'USER_INPUT']:
                        # Search for phrases like 'found', 'remove', 'ai'
                        lines = [l.strip() for l in content.splitlines() if 'ai' in l.lower() or 'remove' in l.lower() or 'found' in l.lower()]
                        print(f"Step {idx} ({source}/{stype}):")
                        for l in lines[:5]:
                            print(f"   {l[:140]}")
        except Exception as e:
            pass
