# CN CW feature coverage

> **历史文档提示（2026-09-19）**：下方矩阵记录的是 0.3.1 阶段，不再代表当前 UI 完成度或包名。当前源码为 `0.3.18`，正式包名 `com.beibei.calculator`（Debug 增加 `.debug`）；函数表、系数输入、频数统计、矩阵/向量记忆、Base-N 表单已有后续实现。最新已验证范围、审查反例及未完成验收以 [Stage 6 接力文档](hand-offs/STAGE6_RELAY.md)和[审查修复记录](audits/2026-09-19/FIXES.md)为准。本轮不宣称完整说明书一致性。

This matrix is the honest implementation boundary for the cn991 clean-room
rebuild at version `0.3.1`; no 999 APK is part of this release.  The source of
truth for the feature list is the 991 sections of the supplied
`fx-991CNCW_999CNCW_CN-1(1).pdf` manual.  “Core API” means a dependency-free
Java engine exists; it does not mean that the Android screen can reach it yet.
“UI wired” means a user can open the mode from the current Android shell and
complete the workflow.  A row marked “not verified” still needs differential
tests against a physical reference or a manual trace.

| Manual area | Core API | UI wired | Verification | Current boundary |
| --- | --- | --- | --- | --- |
| Calculate / COMP | **Partial** — CN CW machine, semantic tokens, separate EXE/relations, scalar parser, common functions, `Ans`, variables, `f(x)`/`g(x)`, angle conversion | **Partial workflow** — Home → Calculate, CN keyboard, editing, history, relation verification and numeric result are reachable; heavy EXE work runs from an isolated snapshot | **Parser + shell regression**; physical traces pending | MathI/MathO layout and exact surd/pi result policy remain incomplete |
| Common scientific functions | **Partial** — trig/inverse/hyperbolic, logarithms, roots, powers, factorial, `nPr`/`nCr`, random, implicit multiplication, ÷R and DMS are parsed | **Partial workflow** — direct keys and catalog insertion exist | **Parser/engine + catalog path tests**; angle/display edge cases pending | Full catalog aliases, domain-error text and every manual range are pending |
| Derivative / Integral / Sum | **Yes (numeric primitive)** — central derivative, adaptive integration and finite summation | **Template insertion + isolated EXE worker** | **Engine tests**; manual UI traces pending | MathI/MathO slot editor and full result paging pending |
| Constants and unit conversions | **Yes (catalog/API)** — independent scientific-constant and conversion registries | **Partial menu** — a small first-pass subset is insertable | **API-level only** | Complete catalog/search, all conversion entries and display formatting pending |
| Input/output and calculation settings | **Partial** — `CnCwSettings`/`CalculatorSettings` model Math/Linear, angle units, Fix/Sci/Norm, engineering symbols, fractions, complex format, decimal mark and separators | **Partial workflow** — settings menus are navigable and change in-memory state | **Compile/API-level only** | Persistence, complete formatter integration and device verification are pending |
| Statistics | **Yes (engine)** — one/two-variable summaries and seven regression families | **Compact workflow** — comma-entry single/two-variable and linear-regression paths return results | **Engine + shell workflow tests**; device parity pending | Table data editor, frequency columns and full result pages pending |
| Function Table | **Yes (engine)** — one function or `f+g` row generation and limits | **Compact workflow** — expression/range/step entry returns row count and boundary rows | **Engine + bridge tests**; device parity pending | Dedicated start/end/step form, scrolling table and editable cells pending |
| Equation | **Partial (engine)** — polynomial roots (degree 1–4), linear systems and Newton/SOLVE primitive | **Compact workflow** — comma-entry coefficients and expression/initial-value SOLVE are reachable | **Engine + bridge tests**; convergence/error UX pending | Dedicated coefficient editors and paged solution screens pending |
| Inequality | **Yes (engine)** — polynomial interval solver | **Compact workflow** — relation code plus coefficients returns interval notation | **Engine + bridge tests**; manual boundary cases pending | Dedicated degree/relation forms and graphical interval renderer pending |
| Complex | **Yes (engine)** — complex arithmetic, polar conversion and expression evaluation | **Partial workflow** — Home entry and generic complex expression/result are reachable | **Engine tests**; DEG/GRAD and display-format parity pending | Dedicated complex catalog/editor and physical traces pending |
| Base-N | **Yes (engine)** — signed 32-bit parse/format and bitwise arithmetic | **Partial workflow** — HEX/DEC/OCT/BIN selection and integer parse/format are reachable | **Engine + shell tests**; overflow/display traces pending | Expression-level bitwise keyboard and all base-specific labels pending |
| Matrix | **Yes (engine)** — MatA–MatD-style values, arithmetic, determinant, inverse, solve | **Compact workflow** — row/column/elements entry returns dimensions and determinant | **Engine + bridge tests**; 4×4 editor traces pending | Matrix grid editor, stored MatA–MatD and catalog actions pending |
| Vector | **Yes (engine)** — 2D/3D add, dot, cross, magnitude, angle | **Compact workflow** — one/two vector comma-entry returns magnitude or dot/angle | **Engine + bridge tests**; display parity pending | Vector editor, stored VctA–VctD and result paging pending |
| Ratio | **Yes (engine)** — both manual proportion forms and exact `Rational` operations | **Compact workflow** — three known values return X for either form | **Engine + shell workflow tests** | Dedicated labeled A/B/C/D coefficient form pending |
| Catalog / Tools / Variables / Functions menus | **Partial** — menu reducer, variables, catalog constants, relation symbols, manual utility functions and unit-conversion registries exist | **Partial workflow** — Home/Back/OK/EXE/directional navigation, relation verification, ÷R, DMS, assignment and first-pass menus render | **Shell path + API regression**; full manual menu differential pending | Several actions expose only a subset of the catalog; menus are still compact rather than full single-select pages |
| History, persistence and export | **Partial** — in-memory calculation history exists | **Partial** — up/down recall in an application | **Compile-level only** | On-disk persistence and versioned export are deliberately deferred |

## Product boundary

Only `cn991` is generated by Gradle.  The product contract is exposed as
generated `BuildConfig` constants:

| Variant | Version | Application ID | Manual applications | `MODEL_EXTENDED` | Distribution / Spreadsheet |
| --- | --- | --- | ---: | --- | --- |
| `cn991Debug` / `cn991Release` | `0.3.1` | `com.codex.cnscientific.calculator991` | 10 | `false` | unavailable / unavailable |

The table shows the release/base ID; debug packages add `.debug`.

Distribution and Spreadsheet are not published in the 991 HOME/menu graph even
if reusable engine classes remain in the shared library.  They are outside the
version 0.3.1 cn991 release and do not count toward its parity boundary.

## Verification language

CN Scientific 991 version 0.3.1 intentionally does **not** claim complete 1:1
parity with the 991 manual.  A “Yes” in the Core API column is an implementation
milestone, not a promise about key labels, formatting, error messages, timing,
or every limit in the manual.  Full parity requires golden traces and
differential checks on a physical 991 reference device; those gates are listed
in [ARCHITECTURE.md](ARCHITECTURE.md).
