export type VoiceAvailability = 'AVAILABLE' | 'UNAVAILABLE';

interface SpeechRecognitionAlternative {
  transcript: string;
}

interface SpeechRecognitionResult {
  isFinal: boolean;
  0: SpeechRecognitionAlternative;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<SpeechRecognitionResult>;
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

interface SpeechRecognitionLike {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onstart: (() => void) | null;
  onresult: ((e: SpeechRecognitionEventLike) => void) | null;
  onerror: ((e: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function getSpeechCtor(): SpeechRecognitionCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as Record<string, unknown>;
  return (w.SpeechRecognition as SpeechRecognitionCtor) ?? (w.webkitSpeechRecognition as SpeechRecognitionCtor) ?? null;
}

export interface VoiceCallbacks {
  onListening: () => void;
  onPartial: (text: string) => void;
  onFinal: (text: string) => void;
  onError: (message: string) => void;
}

export class WebVoiceRecognizer {
  private recognition: SpeechRecognitionLike | null = null;
  private finalText = '';

  availability(): VoiceAvailability {
    return getSpeechCtor() ? 'AVAILABLE' : 'UNAVAILABLE';
  }

  start(offlineOnly: boolean, cb: VoiceCallbacks): boolean {
    const Ctor = getSpeechCtor();
    if (!Ctor) return false;
    if (offlineOnly) {
      // Browsers do not expose an on-device-only guarantee in Web Speech API.
      cb.onError(
        'Offline-only speech recognition is enabled in Settings → AI & voice, and browsers cannot guarantee on-device recognition. Disable that switch to use browser speech input, or type your plan.',
      );
      return true;
    }

    this.stop();
    this.finalText = '';
    const rec = new Ctor();
    rec.continuous = true;
    rec.interimResults = true;
    rec.lang = navigator.language || 'en-US';

    rec.onstart = () => cb.onListening();
    rec.onresult = (event) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const res = event.results[i];
        const transcript = res[0]?.transcript ?? '';
        if (res.isFinal) {
          this.finalText = `${this.finalText} ${transcript}`.trim();
        } else {
          interim = `${interim} ${transcript}`.trim();
        }
      }
      cb.onPartial([this.finalText, interim].filter(Boolean).join(' '));
    };
    rec.onerror = (event) => {
      this.recognition = null;
      cb.onError(`Voice input stopped (${event.error}). You can type your plan instead.`);
    };
    rec.onend = () => {
      this.recognition = null;
      if (this.finalText) {
        cb.onFinal(this.finalText);
        this.finalText = '';
      } else {
        cb.onFinal('');
      }
    };

    try {
      this.recognition = rec;
      rec.start();
      return true;
    } catch {
      this.recognition = null;
      return false;
    }
  }

  finishListening(): void {
    this.recognition?.stop();
  }

  stop(): void {
    if (this.recognition) {
      this.recognition.onend = null;
      this.recognition.onerror = null;
      try {
        this.recognition.stop();
      } catch {
        // ignore
      }
      this.recognition = null;
    }
  }
}
