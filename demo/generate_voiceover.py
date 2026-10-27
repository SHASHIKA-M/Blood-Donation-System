"""Generate per-scene male voiceover audio with edge-tts and measure durations.

Reads  : demo/scenes.json  -> [{"id": "s01_intro", "voice": "...", "text": "..."}, ...]
Writes : demo/audio/<id>.mp3  +  demo/audio/durations.json
"""
import asyncio
import json
import os
import sys

import edge_tts
from mutagen.mp3 import MP3

BASE = os.path.dirname(os.path.abspath(__file__))
AUDIO_DIR = os.path.join(BASE, "audio")
SCENES = os.path.join(BASE, "scenes.json")

VOICE = "en-US-AndrewNeural"          # natural, professional male voice
RATE = "-4%"                          # slightly slower = confident reviewer tone
VOLUME = "+0%"


async def synth(text: str, out_path: str) -> None:
    comm = edge_tts.Communicate(text, VOICE, rate=RATE, volume=VOLUME)
    await comm.save(out_path)


async def main() -> None:
    os.makedirs(AUDIO_DIR, exist_ok=True)
    with open(SCENES, encoding="utf-8") as fh:
        scenes = json.load(fh)

    for sc in scenes:
        out = os.path.join(AUDIO_DIR, f"{sc['id']}.mp3")
        await synth(sc["text"], out)
        dur = MP3(out).info.length
        sc["duration"] = round(dur, 3)
        print(f"{sc['id']:28s} {dur:7.2f}s")

    with open(os.path.join(AUDIO_DIR, "durations.json"), "w", encoding="utf-8") as fh:
        json.dump(scenes, fh, indent=2)

    total = sum(s["duration"] for s in scenes)
    print(f"\nscenes={len(scenes)}  total={total:.1f}s ({total/60:.2f} min)")


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
