# Handoff validation — actually executed checks

**Date:** 9 September 2026  
**Scope:** specification package, Python reference codec, protocol fixtures, and archive integrity.  
**Host Python:** 3.13.5  
**Production applications:** not implemented or built in this planning task.

## Result
The handoff consistency check passed. All **27 reference-codec unit tests passed**. The committed fixture set contains **16 valid frames** with 20-byte and 128-byte fragmentation variants. The acceptance matrix contains **74 unique product criteria**, initially **NOT_RUN**; those are future implementation/hardware requirements, not 74 claimed successes.

The executable tests include 300 deterministic randomized fragmentation trials, every split point of the 115-byte sample snapshot, all 920 possible single-bit flips of that sample, concatenated frames, unknown fields, bounded-noise recovery, maximum metadata, malformed lengths, and checksum validation. The reference encoder/decoder is not a substitute for independent production Kotlin/C implementations; cross-language and device tests remain part of the agent's work.

## Commands executed
```sh
python3 tools/check_handoff.py --tests
python3 -m unittest discover -s protocol -v
```
The first command includes the second suite. Both paths were exercised during preparation. Final packaging also re-runs the consistency/integrity check, verifies ZIP entries and CRCs, and compares archived bytes against the source package.

## Captured reference-suite output
```text
test_all_golden_roundtrips (test_reference_codec.ReferenceCodecTests.test_all_golden_roundtrips) ... ok
test_all_single_bit_corruptions (test_reference_codec.ReferenceCodecTests.test_all_single_bit_corruptions) ... ok
test_bad_command_repeat (test_reference_codec.ReferenceCodecTests.test_bad_command_repeat) ... ok
test_committed_vectors_unchanged (test_reference_codec.ReferenceCodecTests.test_committed_vectors_unchanged) ... ok
test_corrupt_crc_then_valid (test_reference_codec.ReferenceCodecTests.test_corrupt_crc_then_valid) ... ok
test_crc_standard_check (test_reference_codec.ReferenceCodecTests.test_crc_standard_check) ... ok
test_every_split_point (test_reference_codec.ReferenceCodecTests.test_every_split_point) ... ok
test_fixed_state_offsets (test_reference_codec.ReferenceCodecTests.test_fixed_state_offsets) ... ok
test_fragment_bounds (test_reference_codec.ReferenceCodecTests.test_fragment_bounds) ... ok
test_header_layout (test_reference_codec.ReferenceCodecTests.test_header_layout) ... ok
test_large_noise_buffer_bound (test_reference_codec.ReferenceCodecTests.test_large_noise_buffer_bound) ... ok
test_magic_in_text_not_delimiter (test_reference_codec.ReferenceCodecTests.test_magic_in_text_not_delimiter) ... ok
test_maximum_text (test_reference_codec.ReferenceCodecTests.test_maximum_text) ... ok
test_multiple_frames_one_input (test_reference_codec.ReferenceCodecTests.test_multiple_frames_one_input) ... ok
test_no_session (test_reference_codec.ReferenceCodecTests.test_no_session) ... ok
test_noise_resynchronization (test_reference_codec.ReferenceCodecTests.test_noise_resynchronization) ... ok
test_oversize_length_then_valid (test_reference_codec.ReferenceCodecTests.test_oversize_length_then_valid) ... ok
test_random_chunking (test_reference_codec.ReferenceCodecTests.test_random_chunking) ... ok
test_reserved_state_byte_rejected_with_valid_crc (test_reference_codec.ReferenceCodecTests.test_reserved_state_byte_rejected_with_valid_crc) ... ok
test_single_byte_fragments (test_reference_codec.ReferenceCodecTests.test_single_byte_fragments) ... ok
test_snapshot_roundtrip (test_reference_codec.ReferenceCodecTests.test_snapshot_roundtrip) ... ok
test_state_value_validation (test_reference_codec.ReferenceCodecTests.test_state_value_validation) ... ok
test_text_character_limits (test_reference_codec.ReferenceCodecTests.test_text_character_limits) ... ok
test_truncated_frame_waits_and_reset (test_reference_codec.ReferenceCodecTests.test_truncated_frame_waits_and_reset) ... ok
test_unknown_position_duration (test_reference_codec.ReferenceCodecTests.test_unknown_position_duration) ... ok
test_unknown_type_rejected (test_reference_codec.ReferenceCodecTests.test_unknown_type_rejected) ... ok
test_zero_session_or_message_rejected (test_reference_codec.ReferenceCodecTests.test_zero_session_or_message_rejected) ... ok

----------------------------------------------------------------------
Ran 27 tests in 0.179s

OK
PASS: 20 required files; 16 valid vectors; 74 unique acceptance criteria; 27 source groups; 0 local Markdown links; 0 checked file hashes (before final checksum generation); approved PNG 1448x1086.
```

## Scope of the package checks
Required documents/assets exist and are nonempty. Protocol constants match the reference codec. All committed vectors reproduce exactly and decode/re-encode byte-for-byte. Fragment files reassemble to their matching frames. Every cited source ID has a catalog definition. The acceptance IDs are unique. The approved PNG signature/dimensions are valid. SHA256SUMS records final package files; the checker validates it when present.

## Explicitly not claimed
No Android APK or Flipper FAP was built here. No physical pairing, Apple Music metadata inspection, locked-screen volume test, native FAP screenshot, end-to-end latency test, or battery measurement was performed. The approved render remains a concept, not a screenshot of a running application. Source review supports the proposed architecture, but the first implementation milestone still has to prove the real custom GATT path.

## Re-running after development begins
Use Python 3.10 or newer. The original archive's hashes detect edits by design. After intentionally changing the specification or acceptance matrix, run `python3 tools/check_handoff.py --skip-integrity --tests` for structural/reference checks, or regenerate the integrity manifest deliberately. Production test results belong in the separate build/hardware reports required by the specification.
