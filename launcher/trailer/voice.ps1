# Speaks a line with a Windows voice into a 44.1 kHz WAV: voice.ps1 -Text "Welcome to Aurora" -Out out\voice-welcome.wav
param([string]$Text, [string]$Out, [int]$Rate = -2, [string]$Voice = 'Microsoft Zira Desktop')
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice($Voice)
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(44100, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$s.SetOutputToWaveFile($Out, $format)
$s.Rate = $Rate
$s.Speak($Text)
$s.Dispose()
