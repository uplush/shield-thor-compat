# Third-party notices and provenance

The root MIT license applies to this project's original additions, not to all
upstream source trees or to the original game. Original notices are preserved.

## Mesa / Gallium / Zink

- Base: Swung0x48/mesa commit `9ea0decb3176d34e578c3c603fd9c8e29b580f71`.
- The tested library reports Mesa 23.0.4; the fork commit, not the version string,
  identifies the source required for the consolidated patch.
- Default/core/Gallium license: MIT; consult individual source files.
- See `LICENSES/Mesa-original-license.rst` and `LICENSES/Mesa-file-notices.txt`.
- Copyright notices in affected sources include Collabora Ltd., Valve Corporation
  and other Mesa contributors, retained in the notice file.

Bindless work was adapted with reference to these upstream patches by Mike Blumenkrantz:

- `a7cd6c952d53159140fd6cda06c0e66b8674ee84`, share bindless handles between contexts;
  <https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/45450>.
- `1e07f58c62619d692a661c6879646f4a4fc6031b`, initialize bindless when binding a shader;
  <https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/37139>.

Original patch messages (including attribution and generation metadata) are retained
under `provenance/`. The consolidated backport is not identical to those upstream changes.
Do not apply the reference patches a second time.

## Khronos Vulkan headers / registry

- Existing `vulkan_core.h`: Copyright 2015–2022 The Khronos Group Inc., Apache-2.0.
- Existing `vk.xml`: Copyright 2015–2022 The Khronos Group Inc., Apache-2.0 OR MIT.
- Legacy-vertex definitions were adapted from Vulkan-Headers `v1.4.304`:
  <https://github.com/KhronosGroup/Vulkan-Headers/tree/v1.4.304>.
- Modified paths and hashes are in `patches/manifest.json`; the patch marks the backport.
- `LICENSES/Apache-2.0.txt` and `LICENSES/Vulkan-Headers.md` retain the applicable texts.

## Components not redistributed here

The Odin 3 runtime profile references the separately distributed Stable Turnip sync V2
release by whitebelyash:
<https://github.com/whitebelyash/AdrenoToolsDrivers/releases/tag/stu_v2>.
The profile records the tested library hash and the upstream source branch reference;
this repository does not bundle that library or claim a pinned, reproducible build of it.
The small Odin subtitle patch definitions contain only exact-version modification
regions, not the input game binaries, subtitle dialogue or extracted cue tables.

Android SDK/NDK, JDK, libdrm, Turnip, system libraries, original game code/resources,
and private vendor libraries are not bundled in this source release.
Using their names to describe interfaces or dependencies does not relicense them.
The new Java classes retain integration package names for ABI compatibility;
their package names are not a claim of NVIDIA authorship or endorsement.

## Source export

`provenance/source-files.json` records the independent helper/test source hashes
before and after adding MIT notices and normalizing line endings. No private
filesystem roots, device identifiers, signatures or credentials are included.
AI-assisted implementation/testing was used during this compatibility research.
