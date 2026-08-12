#!/usr/bin/env python3
"""Encode gray-flow secrets for OracleSecrets.kt.

Must stay in sync with OracleCodec.SEED_PHRASE and OracleCodec.STREAM_LEN.
Run from the project root:

    python tools/pack_secrets.py

Paste the printed intArrayOf lines into OracleSecrets.kt.
"""

from __future__ import annotations

# Mirror OracleCodec.kt exactly.
SEED_PHRASE = "OlymFlow_p8x!v27"
STREAM_LEN = 27

PLAINTEXT = {
    "CONFIG_ENDPOINT_BYTES": "https://olymmpussurge.com/config.php",
    "ATTRIBUTION_KEY_BYTES": "6MzAAqPD2jE4cXuwRwexqC",
    "MESSAGING_PROJECT_BYTES": "893534989184",
    "CHROME_VERSION_BYTES": "149.0.7823.147",
    "WEBKIT_VERSION_BYTES": "537.36",
}


def stream() -> list[int]:
    h = 0x811C9DC5
    for c in SEED_PHRASE:
        h = (h ^ ord(c)) & 0xFFFFFFFF
        h = (h * 0x01000193) & 0xFFFFFFFF
    state = h if h else 0x9E3779B9
    out: list[int] = []
    for _ in range(STREAM_LEN):
        state = (state ^ ((state << 13) & 0xFFFFFFFF)) & 0xFFFFFFFF
        state = (state ^ (state >> 17)) & 0xFFFFFFFF
        state = (state ^ ((state << 5) & 0xFFFFFFFF)) & 0xFFFFFFFF
        out.append((state >> 16) & 0xFF)
    return out


def pack(plaintext: str) -> list[int]:
    s = stream()
    return [(ord(ch) ^ s[i % STREAM_LEN] ^ (i & 0xFF)) & 0xFF for i, ch in enumerate(plaintext)]


def main() -> None:
    ks = stream()
    print(f"seed={SEED_PHRASE!r}  stream_len={STREAM_LEN}")
    for name, plain in PLAINTEXT.items():
        encoded = pack(plain)
        joined = ", ".join(str(b) for b in encoded)
        print(f"\n// Plaintext: {plain!r}")
        print(f"val {name} = intArrayOf({joined})")
        decoded = "".join(chr((b ^ ks[i % STREAM_LEN] ^ (i & 0xFF)) & 0xFF) for i, b in enumerate(encoded))
        assert decoded == plain, f"round-trip failed for {name}"


if __name__ == "__main__":
    main()
