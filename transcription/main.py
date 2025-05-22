import whisper

model = whisper.load_model("turbo")
result = model.transcribe("audio/transcription-test.flac")
print(result["text"])