import sys
import argparse
import os
import re
from pydub import AudioSegment
import subprocess
from dotenv import load_dotenv
import json
import time
import logging
import requests

logging.basicConfig(level=logging.INFO, format='[%(asctime)s] %(levelname)s: %(message)s')
logger = logging.getLogger(__name__)
load_dotenv()
ASSEMBLY_KEY = os.getenv("ASSEMBLYAI_API_KEY")
HF_TOKEN = os.getenv("HF_TOKEN")

def find_speakers_and_inputs(directory):
    ids = {}  # maps uuid -> name
    empty_ids = {} # maps name -> ""
    input_media = []
    to_be_transcribed = None

    # Matches: sample-<name>_<uuid>.<ext>
    sample_pattern = re.compile(r"sample-(\w+)_([^_]+)\.\w+$", re.IGNORECASE)

    media_extensions = (".mp3", ".wav", ".m4a", ".flac", ".aac", ".ogg", ".mp4", ".mov", "opus")

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
            elif filename != "file-separator.wav":
                to_be_transcribed = file_path

    if to_be_transcribed:
        input_media.append(to_be_transcribed)
    return empty_ids, ids, input_media

def cut_and_convert_to_wav(input_file, output_wav, limit_duration=True):
    """Convert any audio/video file to WAV using FFmpeg."""
    if limit_duration:
        subprocess.run(["ffmpeg", "-y", "-i", input_file, "-t", "20", output_wav], check=True)
    else:
        subprocess.run(["ffmpeg", "-y", "-i", input_file, output_wav], check=True)

def merge_wav_files(wav_paths, output_path):
    """Merge a list of WAV files sequentially into one."""
    if not wav_paths:
        raise ValueError("No WAV files provided to merge.")

    combined = AudioSegment.empty()
    for wav_file in wav_paths:
        combined += AudioSegment.from_wav(wav_file)

    combined.export(output_path, format="wav")

def convert_and_merge(inputs, output, directory, use_file_separator=True, silence_gap=10):
    sample_inputs = inputs[:-1]
    real_input = inputs[-1]

    sample_wavs = []
    for idx, inp in enumerate(sample_inputs):
        out = os.path.join(directory, f"tmp_sample_{idx}.wav")
        cut_and_convert_to_wav(inp, out)
        sample_wavs.append(out)

    if use_file_separator:
        sample_wavs.append("file-separator.wav")

    # Convert real input
    real_wav = os.path.join(directory, "tmp_real.wav")
    cut_and_convert_to_wav(real_input, real_wav, False)

    # Calculate total sample duration
    sample_duration = sum(AudioSegment.from_wav(w).duration_seconds for w in sample_wavs)
    print(f"Total sample duration: {sample_duration:.2f} seconds")

    # Define a fixed silence gap between samples and the real input
    silence = AudioSegment.silent(duration=int(silence_gap * 1000))  # in ms

    # Merge: [samples] + [silence] + [real audio]
    combined = AudioSegment.empty()
    for w in sample_wavs:
        combined += AudioSegment.from_wav(w)
        combined += silence  # Add silence after each sample
    combined += AudioSegment.from_wav(real_wav)

    combined.export(output, format="wav")

    # Clean up temp files
    for wav_file in sample_wavs:
        try:
            if wav_file.startswith("tmp_sample_"):
                os.remove(wav_file)
                print(f"Deleted temporary file: {wav_file}")
        except OSError as e:
            print(f"Error deleting {wav_file}: {e}")

    # Total offset is sample_duration
    return sample_duration + silence_gap * (len(sample_wavs) - 1) + (int(silence_gap / 2)), silence_gap

def transcribe_local_whisperx(merged, offset, silence_gap, empty_speaker_ids, speaker_ids, project_id, speaker_amount):
    import whisperx

    device = "cpu"  # or "cpu"
    batch_size = 16
    compute_type = "int8"  # use "int8" for CPU

    print("Loading WhisperX...")
    model_dir = "whisper_model/"
    model = whisperx.load_model("turbo", device, compute_type=compute_type, download_root=model_dir)

    print(f"Transcribing {merged}...")
    result = model.transcribe(merged, batch_size=batch_size)
    model_a, metadata = whisperx.load_align_model(language_code=result["language"], device=device)
    result = whisperx.align(result["segments"], model_a, metadata, merged, device)

    print(f"Diarizing {merged}...")
    diarize_model = whisperx.diarize.DiarizationPipeline(use_auth_token=HF_TOKEN, device=device)
    diarize_segments = diarize_model(merged, min_speakers=speaker_amount, max_speakers=speaker_amount)
    result = whisperx.assign_word_speakers(diarize_segments, result)

    segment_counter = 0
    segment_index_counter = 0
    if len(result["segments"]) < len(empty_speaker_ids):
        logger.warning("Not enough segments for the number of speakers. Assigning unknown speakers and ids.")
        # Assign unknown speaker IDs to segments
        for i in range(len(result["segments"])):
            result["segments"][i]["speaker_id"] = "Unknown ID"
            result["segments"][i]["speaker"] = "Unknown Speaker"
            result["segments"][i]["index"] = i

    else:
        for actual_id in empty_speaker_ids.keys():
            current_speaker = result["segments"][segment_counter].get("speaker", None)
            for segment in result["segments"][segment_counter:]:
                segment["index"] = segment_index_counter
                segment_index_counter += 1
                if segment.get("speaker") == current_speaker:
                    empty_speaker_ids[actual_id] = current_speaker
                    segment["speaker_id"] = actual_id
                    segment["speaker"] = speaker_ids[actual_id]
                elif segment.get("speaker") is None:
                    segment["speaker_id"] = "Unknown ID"
                    segment["speaker"] = "Unknown Speaker"
                else:
                    segment_counter = result["segments"].index(segment)
                    break

        unknown_speaker_index = 0
        unknown_speakers = {}
        for segment in result["segments"][segment_counter:]:
            segment["index"] = segment_index_counter
            segment_index_counter += 1
            if segment.get("speaker") is None:
                segment["speaker_id"] = "Unknown ID"
                segment["speaker"] = "Unknown Speaker"
                continue
            key = next((k for k, v in empty_speaker_ids.items() if v == segment["speaker"]), None)
            if key is not None:
                segment["speaker_id"] = key
                segment["speaker"] = speaker_ids.get(segment["speaker_id"], "Unknown Speaker")
            else:
                if not segment["speaker"] in unknown_speakers.keys():
                    unknown_speakers[segment["speaker"]] = unknown_speaker_index
                    unknown_speaker_index += 1
                segment["speaker_id"] = f"Unknown ID {unknown_speakers[segment['speaker']]}"
                segment["speaker"] = f"Unknown Speaker {unknown_speakers[segment['speaker']]}"

        # # --- PRINT DIARIZATION ---
        # print("\nSpeaker assignment results:")
        # print("Speaker\tStart\tEnd\tText")
        # for segment in result["segments"]:
        #     print(
        #         f"{segment['speaker']}\t{segment['speaker_id']}\t{segment['start']:.2f}\t{segment['end']:.2f}\t{segment['text']}")

    real_segments = []
    index = 0
    for seg in result["segments"]:
        if seg["start"] >= offset:
            rebased = seg.copy()
            rebased["index"] = index
            rebased["start"] -= max(0, offset - (int(silence_gap / 2)))
            rebased["end"] -= max(0, offset - (int(silence_gap / 2)))
            real_segments.append(rebased)
            index += 1

    # create result json structure with each segment containing speaker, start, end, and text
    result_json = []
    for segment in real_segments:
        result_json.append({
            "metadata": {
                "type": "transcription",
                "user": segment["speaker_id"] if not segment["speaker_id"].startswith("Unknown ID") else None,
                "timestamp": args.timestamp,
                "projectId": project_id
            },
            "content": {
                "index": segment["index"],
                "start": segment["start"],
                "end": segment["end"],
                "text": segment["text"],
                "userName": segment["speaker"],
                "userId": segment["speaker_id"]
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

def transcribe_cloud_assemblyai(merged, offset, empty_speaker_ids, speaker_ids, project_id, speaker_amount):
    # Upload file to AssemblyAI
    upload_url = "https://api.eu.assemblyai.com/v2/upload"
    headers = {"authorization": ASSEMBLY_KEY, "content-type": "application/octet-stream"}
    logger.info(f"Uploading {merged} to AssemblyAI...")
    try:
        with open(merged, 'rb') as f:
            response = requests.post(upload_url, headers=headers, data=f)
        response.raise_for_status()
    except Exception as e:
        logger.error(f"Failed to upload file: {e}")
        sys.exit(1)

    upload_resp = response.json()
    if "upload_url" not in upload_resp:
        logger.error(f"Unexpected upload response: {upload_resp}")
        sys.exit(1)
    audio_url = upload_resp["upload_url"]
    logger.info(f"Upload successful. URL: {audio_url}")

    # Submit transcription request
    transcribe_url = "https://api.eu.assemblyai.com/v2/transcript"
    transcript_request = {
        "audio_url": audio_url,
        "speaker_labels": True,
        "format_text": True,
        "punctuate": True,
        "speech_model": "slam-1",
        "speakers_expected": speaker_amount
    }
    headers = {"authorization": ASSEMBLY_KEY, "content-type": "application/json"}
    logger.info("Submitting transcription request (speaker diarization enabled)...")
    try:
        response = requests.post(transcribe_url, json=transcript_request, headers=headers)
        response.raise_for_status()
    except Exception as e:
        logger.error(f"Failed to submit transcript request: {e}")
        sys.exit(1)

    transcript_resp = response.json()
    transcript_id = transcript_resp.get("id")
    if not transcript_id:
        logger.error(f"Failed to get transcript ID. Response: {transcript_resp}")
        sys.exit(1)
    logger.info(f"Transcript request submitted. ID: {transcript_id}")

    # Poll until transcription is complete
    status = transcript_resp.get("status")
    check_url = f"{transcribe_url}/{transcript_id}"
    while status not in ("completed", "error"):
        logger.info(f"Waiting for transcription (current status: {status})...")
        time.sleep(5)  # wait before checking again
        try:
            response = requests.get(check_url, headers=headers)
            response.raise_for_status()
        except Exception as e:
            logger.error(f"Error checking transcript status: {e}")
            sys.exit(1)
        result = response.json()
        status = result.get("status")
        if status == "error":
            logger.error(f"Transcription failed: {result.get('error')}")
            sys.exit(1)

    logger.info("Transcription completed successfully.")
    print(f"\nTranscription result: {result}")

    # Extract utterances from transcript
    utterances = result.get("utterances", [])
    segments = []
    for utt in utterances:
        segment = {
            "start": utt.get("start"),
            "end": utt.get("end"),
            "speaker": utt.get("speaker"),
            "text": utt.get("text")
        }
        segments.append(segment)

    print(utterances)

    logger.info(f"Extracted {len(segments)} segments from transcription.")

    segment_counter = 0
    speaker_counter = 0
    current_speaker = None

    if len(segments) < len(empty_speaker_ids):
        logger.warning("Not enough segments for the number of speakers. Assigning unknown speakers and ids.")
        # Assign unknown speaker IDs to segments
        for i in range(len(segments)):
            segments[i]["speaker_id"] = f"Unknown ID {i}"
            segments[i]["speaker"] = f"Unknown Speaker {i}"
            segments[i]["index"] = i

        real_segments = segments
    else:

        for segment in segments:
            if segment["start"] >= offset * 1000:
                break
            text = segment.get("text", "")
            words = re.findall(r'\w+', text)
            count = len(words)
            if count < 4:
                segment_counter += 1
                continue
            if segment["speaker"] != current_speaker and speaker_counter < len(empty_speaker_ids.keys()) and segment["start"] < offset * 1000:
                empty_speaker_ids[list(speaker_ids.keys())[speaker_counter]] = segment["speaker"]
                speaker_counter += 1
            if segment["start"] >= offset * 1000:
                break
            segment_counter += 1


        print(f"Empty speaker IDs: {empty_speaker_ids}")
        print(f"Speaker IDs: {speaker_ids}")

        real_segments = segments[segment_counter:]

        index_counter = 0
        first = True
        start_offset = 0
        unknown_speaker_index = 0
        unknown_speakers = {}
        for segment in real_segments:
            if segment.get("speaker") is None:
                segment["speaker_id"] = "Unknown ID"
                segment["speaker"] = "Unknown Speaker"
                continue
            segment["index"] = index_counter
            if first:
                start_offset = segment["start"]
                first = False
            segment["start"] -= start_offset
            segment["end"] -= start_offset
            key = next((k for k, v in empty_speaker_ids.items() if v == segment["speaker"]), None)
            if key is not None:
                segment["speaker_id"] = key
                segment["speaker"] = speaker_ids.get(segment["speaker_id"], "Unknown Speaker")
            else:
                if not segment["speaker"] in unknown_speakers.keys():
                    unknown_speakers[segment["speaker"]] = unknown_speaker_index
                    unknown_speaker_index += 1
                segment["speaker_id"] = f"Unknown ID {unknown_speakers[segment['speaker']]}"
                segment["speaker"] = f"Unknown Speaker {unknown_speakers[segment['speaker']]}"
            index_counter += 1

    # # --- PRINT DIARIZATION ---
    # print("\nSpeaker assignment results:")
    # print("Speaker\tStart\tEnd\tText")
    # for segment in real_segments:
    #     print(
    #         f"{segment['speaker']}\t{segment['speaker_id']}\t{segment['start']:.2f}\t{segment['end']:.2f}\t{segment['text']}")

    # create result json structure with each segment containing speaker, start, end, and text
    result_json = []
    for segment in real_segments:
        result_json.append({
            "metadata": {
                "type": "transcription",
                "user": segment["speaker_id"] if not segment["speaker_id"].startswith("Unknown ID") else None,
                "timestamp": args.timestamp,
                "projectId": project_id
            },
            "content": {
                "index": segment["index"],
                "start": segment["start"],
                "end": segment["end"],
                "text": segment["text"],
                "userName": segment["speaker"],
                "userId": segment["speaker_id"]
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


if __name__ == "__main__":
    load_dotenv()
    ASSEMBLY_KEY = os.getenv("ASSEMBLYAI_API_KEY")
    HF_TOKEN = os.getenv("HF_TOKEN")
    USE_CLOUD = os.getenv("USE_CLOUD", "false").lower() == "true"
    if not ASSEMBLY_KEY or not HF_TOKEN:
        logger.error("ASSEMBLYAI_API_KEY or HF_TOKEN not found in environment variables.")
        sys.exit(1)

    parser = argparse.ArgumentParser(description="Process media files in a directory with a timestamp.")
    parser.add_argument("directory", help="Directory containing media files")
    parser.add_argument("speaker_amount", type=int, help="Number of speakers in the recording")
    parser.add_argument("timestamp", help="Unix Timestamp for the recording")
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
    if USE_CLOUD:
        offset, silence_gap = convert_and_merge(inputs, merged, args.directory, use_file_separator=False, silence_gap=10)
    else:
        offset, silence_gap = convert_and_merge(inputs, merged, args.directory, use_file_separator=True, silence_gap=10)

    if USE_CLOUD:
        transcribe_cloud_assemblyai(merged, offset, empty_speaker_ids, speaker_ids, project_id, args.speaker_amount)
    else:
        transcribe_local_whisperx(merged, offset, silence_gap, empty_speaker_ids, speaker_ids, project_id, args.speaker_amount)
