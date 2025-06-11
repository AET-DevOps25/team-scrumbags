import whisperx
import sys
import argparse
import os
import re
from pydub import AudioSegment
import subprocess
from dotenv import load_dotenv
import json

def find_speakers_and_inputs(directory):
    ids = {}  # maps uuid -> name
    empty_ids = {} # maps name -> ""
    input_media = []

    # Matches: sample-<name>_<uuid>.<ext>
    sample_pattern = re.compile(r"sample-(\w+)_([^_]+)\.\w+$", re.IGNORECASE)

    media_extensions = (".mp3", ".wav", ".m4a", ".flac", ".aac", ".ogg", ".mp4", ".mov", "opus")

    to_be_transcribed = ""

    for filename in os.listdir(directory):
        file_path = os.path.join(directory, filename)

        if os.path.isfile(file_path) and filename.lower().endswith(media_extensions):
            speaker_match = sample_pattern.match(filename)
            if speaker_match:
                speaker_name = speaker_match.group(1)
                speaker_id = speaker_match.group(2)
                ids[speaker_id] = speaker_name
                empty_ids[speaker_id] = ""
                input_media.append(file_path)
            else:
                to_be_transcribed = file_path
    
    input_media.append(to_be_transcribed)
    return empty_ids, ids, input_media

def convert_to_wav(input_file, output_wav):
    """Convert any audio/video file to WAV using FFmpeg."""
    subprocess.run(["ffmpeg", "-y", "-i", input_file, output_wav], check=True)

def merge_wav_files(wav_paths, output_path):
    """Merge a list of WAV files sequentially into one."""
    if not wav_paths:
        raise ValueError("No WAV files provided to merge.")

    combined = AudioSegment.empty()
    for wav_file in wav_paths:
        combined += AudioSegment.from_wav(wav_file)

    combined.export(output_path, format="wav")

def convert_and_merge(inputs, output, directory):
    """
    Converts each input to WAV, sums the durations of all samples EXCEPT
    the last one, merges everything into `output`, then returns the samples-duration.
    """
    # convert all to WAV
    wavs = []
    for idx, inp in enumerate(inputs):
        out = os.path.join(directory, f"tmp_{idx}.wav")
        convert_to_wav(inp, out)
        wavs.append(out)

    # compute offset = total duration of sample clips
    sample_durations = [
        AudioSegment.from_wav(w).duration_seconds
        for w in wavs[:-1]
    ]
    offset = sum(sample_durations)

    # merge into single WAV
    merge_wav_files(wavs, output)

    for wav_file in wavs:
        try:
            os.remove(wav_file)
            print(f"Deleted temporary file: {wav_file}")
        except OSError as e:
            print(f"Error deleting {wav_file}: {e}")

    print(f"Merged WAV with offset {offset:.2f}s → {output}")
    return offset

if __name__ == "__main__":
    load_dotenv()
    parser = argparse.ArgumentParser(description="Process media files in a directory")
    parser.add_argument("directory", help="Directory containing media files")
    args = parser.parse_args()

    empty_speaker_ids, speaker_ids, inputs = find_speakers_and_inputs(args.directory)

    project_id = ""

    match = re.match(r"/tmp/media-([^_]+)_([^_]+)$", args.directory)
    if match:
        project_id = match.group(1)
        print("Project ID: ", project_id)
    else:
        print("No match for project id found.")

    merged = f"{args.directory}/merged.wav"
    offset = convert_and_merge(inputs, merged, args.directory)

    device = "cpu"  # or "cpu"
    batch_size = 16
    compute_type = "int8"  # use "int8" for CPU

    # --- LOAD MODEL ---
    print("Loading WhisperX...")
    model_dir = "whisper_model/"
    model = whisperx.load_model("turbo", device, compute_type=compute_type)

    # --- PROCESS FILE ---
    print(f"Transcribing {merged}...")
    result = model.transcribe(merged, batch_size=batch_size)
    model_a, metadata = whisperx.load_align_model(language_code=result["language"], device=device)
    result = whisperx.align(result["segments"], model_a, metadata, merged, device)

    # --- PRINT TRANSCRIPTION ---
    print(f"\nTranscription for {merged}:")
    for segment in result["segments"]:
        print(f"{segment['start']:.2f} - {segment['end']:.2f}: {segment['text']}")

    print(f"Diarizing {merged}...")
    diarize_model = whisperx.diarize.DiarizationPipeline(use_auth_token=os.getenv("HF_TOKEN"), device=device)
    diarize_segments = diarize_model(merged)
    result = whisperx.assign_word_speakers(diarize_segments, result)

    # --- CONFIGURE DIARIZATION ---
    speaker_counter = 0
    segment_counter = 0
    segment_index_counter = 0
    for actual_id in empty_speaker_ids.keys():
        current_speaker = result["segments"][segment_counter].get("speaker", None)
        for segment in result["segments"][segment_counter:]:
            segment["index"] = segment_index_counter
            segment_index_counter += 1
            if segment.get("speaker") == current_speaker:
                empty_speaker_ids[actual_id] = current_speaker
                segment["speaker_id"] = actual_id
                segment["speaker"] = speaker_ids.get(actual_id, "Unknown Speaker")
            else:
                segment_counter = result["segments"].index(segment)
                break

    for segment in result["segments"][segment_counter:]:
        segment["index"] = segment_index_counter
        segment_index_counter += 1
        key = next((k for k, v in empty_speaker_ids.items() if v == segment["speaker"]), None)
        if key is not None:
            segment["speaker_id"] = key
            segment["speaker"] = speaker_ids.get(segment["speaker_id"], "Unknown Speaker")
        else:
            segment["speaker_id"] = "Unknown ID"
            segment["speaker"] = "Unknown Speaker"

    # --- PRINT DIARIZATION ---
    print("\nSpeaker assignment results:")
    print("Speaker\tStart\tEnd\tText")
    for segment in result["segments"]:
        print(f"{segment['speaker']}\t{segment['speaker_id']}\t{segment['start']:.2f}\t{segment['end']:.2f}\t{segment['text']}")

    # --- FILTER & REBASE ---
    real_segments = []
    for seg in result["segments"]:
        if seg["start"] >= offset:
            # clone & shift times
            rebased = seg.copy()
            rebased["start"] -= offset
            rebased["end"]   -= offset
            real_segments.append(rebased)

    # create result json structure with each segment containing speaker, start, end, and text
    result_json = []
    for segment in real_segments:
        result_json.append({
            "metadata": {
                "type": "transcription",
                "user": segment["speaker_id"],
                "timestamp": "todo",
                "project_id": project_id
            },
            "content": {
                "index": segment["index"],
                "start": segment["start"],
                "end": segment["end"],
                "text": segment["text"],
                "speaker": segment["speaker"],
                "speaker_id": segment["speaker_id"]
            }
        })

    print(result_json)

    # save result to json file
    output_json = f"{args.directory}/transcription_result.json"
    try:
        with open(output_json, "w", encoding="utf-8") as f:
            json.dump(result_json, f, ensure_ascii=False, indent=2)
        print(f"\nTranscription and diarization results saved to: {output_json}")
    except Exception as e:
        print(f"Error saving result to JSON: {e}")
