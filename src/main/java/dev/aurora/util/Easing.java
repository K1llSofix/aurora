package dev.aurora.util;

/** Easing curves for timed animations. Input and output are in [0, 1]. */
public enum Easing {
	LINEAR {
		@Override
		public float apply(float t) {
			return t;
		}
	},
	OUT_QUAD {
		@Override
		public float apply(float t) {
			return 1 - (1 - t) * (1 - t);
		}
	},
	OUT_CUBIC {
		@Override
		public float apply(float t) {
			float u = 1 - t;
			return 1 - u * u * u;
		}
	},
	IN_OUT_CUBIC {
		@Override
		public float apply(float t) {
			return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
		}
	},
	OUT_QUINT {
		@Override
		public float apply(float t) {
			float u = 1 - t;
			return 1 - u * u * u * u * u;
		}
	},
	OUT_EXPO {
		@Override
		public float apply(float t) {
			return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
		}
	},
	OUT_BACK {
		@Override
		public float apply(float t) {
			float c1 = 1.70158f;
			float c3 = c1 + 1;
			float u = t - 1;
			return 1 + c3 * u * u * u + c1 * u * u;
		}
	},
	IN_OUT_SINE {
		@Override
		public float apply(float t) {
			return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
		}
	};

	public abstract float apply(float t);

	public float clamped(float t) {
		return apply(t < 0 ? 0 : (t > 1 ? 1 : t));
	}
}
