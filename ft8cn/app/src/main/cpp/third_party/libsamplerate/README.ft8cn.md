# libsamplerate provenance

This directory contains the files required by FT8CN from
[libsamplerate](https://github.com/libsndfile/libsamplerate) tag `0.2.1`,
commit `a147b6dc4046deda22b2aa772b348717de772e2b`.
The only import normalization is removal of one trailing blank line from
`common.h`, `samplerate.c`, and `samplerate.h`; executable source is unchanged.

The source family is pinned from the v0.93 `libft8cn.so` evidence, not from its
version string. The binary exports `src_clone`, which first appears in 0.2.0,
and its `src_short_to_float_array` still uses the reverse loop present through
0.2.1. Tag 0.2.2 changed that implementation. Tags 0.2.0 and 0.2.1 have
identical `src/` and `include/samplerate.h` content, so the retained binary
cannot distinguish those two release labels; 0.2.1 is the last exact source
family and is used as the reproducible pin.

The library is BSD-2-Clause licensed. `COPYING` is the upstream license file
and must accompany source and binary redistributions as required by its terms.
