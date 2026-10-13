// Câmera + detecção de pose com MediaPipe Pose Landmarker (roda 100% no aparelho).

const VISION_BASE = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14';
const MODEL_URL =
  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task';

// Ligações entre pontos para desenhar o "esqueleto".
const SKELETON = [
  [11, 12], [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 23], [12, 24], [23, 24], [23, 25], [25, 27], [24, 26], [26, 28],
];

let landmarkerPromise = null;

export function loadPoseLandmarker() {
  landmarkerPromise ??= (async () => {
    const { FilesetResolver, PoseLandmarker } = await import(`${VISION_BASE}/vision_bundle.mjs`);
    const fileset = await FilesetResolver.forVisionTasks(`${VISION_BASE}/wasm`);
    const create = (delegate) =>
      PoseLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL_URL, delegate },
        runningMode: 'VIDEO',
        numPoses: 1,
      });
    try {
      return await create('GPU');
    } catch {
      return await create('CPU');
    }
  })().catch((err) => {
    landmarkerPromise = null; // permite tentar de novo
    throw err;
  });
  return landmarkerPromise;
}

export class PoseTracker {
  constructor(video, canvas) {
    this.video = video;
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.stream = null;
    this.raf = 0;
    this.running = false;
    this.highlight = [];
  }

  // onFrame(landmarks | null, aspect, now) é chamado a cada quadro analisado.
  async start(onFrame) {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Este navegador não dá acesso à câmera (é preciso HTTPS).');
    }
    this.running = true;
    const landmarker = loadPoseLandmarker();
    this.stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false,
    });
    if (!this.running) return this.stop();
    this.video.srcObject = this.stream;
    await this.video.play();
    const model = await landmarker;
    if (!this.running) return;

    let lastTime = -1;
    const loop = () => {
      if (!this.running) return;
      const { video } = this;
      if (video.readyState >= 2 && video.currentTime !== lastTime) {
        lastTime = video.currentTime;
        const now = performance.now();
        const result = model.detectForVideo(video, now);
        const landmarks = result.landmarks?.[0] ?? null;
        const aspect = video.videoWidth / video.videoHeight || 1;
        this.draw(landmarks);
        onFrame(landmarks, aspect, now);
      }
      this.raf = requestAnimationFrame(loop);
    };
    loop();
  }

  stop() {
    this.running = false;
    cancelAnimationFrame(this.raf);
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.video.srcObject = null;
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
  }

  draw(landmarks) {
    const { canvas, ctx, video } = this;
    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (!landmarks) return;

    const pt = (i) => [landmarks[i].x * canvas.width, landmarks[i].y * canvas.height];
    const visible = (i) => (landmarks[i]?.visibility ?? 1) > 0.5;
    const hot = new Set(this.highlight);
    const lw = Math.max(3, canvas.width / 160);

    ctx.lineCap = 'round';
    for (const [a, b] of SKELETON) {
      if (!visible(a) || !visible(b)) continue;
      ctx.strokeStyle = hot.has(a) && hot.has(b) ? '#c6ff3d' : 'rgba(255,255,255,0.75)';
      ctx.lineWidth = hot.has(a) && hot.has(b) ? lw * 1.6 : lw;
      ctx.beginPath();
      ctx.moveTo(...pt(a));
      ctx.lineTo(...pt(b));
      ctx.stroke();
    }
    for (const i of new Set(SKELETON.flat())) {
      if (!visible(i)) continue;
      ctx.fillStyle = hot.has(i) ? '#c6ff3d' : '#ff7a1a';
      ctx.beginPath();
      ctx.arc(...pt(i), lw * 1.3, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}
