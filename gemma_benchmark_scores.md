# Google Gemma Benchmark Scores

## Gemma 4 2B (E2B - Effective 2B Parameters)
**Source:** Hugging Face - google/gemma-4-E2B-it
**URL:** https://huggingface.co/google/gemma-4-E2B-it

### Benchmark Results (from official model card):

| Benchmark | Gemma 4 31B | Gemma 4 26B A4B | Gemma 4 E4B | Gemma 4 E2B | Gemma 3 27B (no think) |
|-----------|-------------|-----------------|-------------|-------------|------------------------|
| **MMLU Pro** | 85.2% | 82.6% | 69.4% | **60.0%** | 67.6% |
| **MMMLU** | 88.4% | 86.3% | 76.6% | **67.4%** | 70.7% |
| **MATH-Vision** | 85.6% | 82.4% | 59.5% | **52.4%** | 46.0% |
| AIME 2026 no tools | 89.2% | 88.3% | 42.5% | 37.5% | 20.8% |
| LiveCodeBench v6 | 80.0% | 77.1% | 52.0% | 44.0% | 29.1% |
| Codeforces ELO | 2150 | 1718 | 940 | 633 | 110 |
| GPQA Diamond | 84.3% | 82.3% | 58.6% | 43.4% | 42.4% |
| BigBench Extra Hard | 74.4% | 64.8% | 33.1% | 21.9% | 19.3% |

### Key Points:
- **HumanEval is NOT reported** in the Gemma 4 benchmark table
- The primary coding benchmark is **LiveCodeBench v6** (44.0% for E2B)
- MMLU Pro is the standard MMLU benchmark shown (60.0% for E2B)

---

## Gemma 3 2B - DOES NOT EXIST
There is no Gemma 3 2B model. The Gemma 3 family consists of:
- Gemma 3 27B
- Gemma 3 12B
- Gemma 3 7B

If you need a 2B model, consider:
- **Gemma 2 2B**: https://huggingface.co/google/gemma-2-2b-it (may require access request)

---

## Sources:
1. Hugging Face Model Card: https://huggingface.co/google/gemma-4-E2B-it
2. Google Gemma 4 Launch Blog: https://blog.google/intelligence/gemma-4/
3. Google AI Documentation: https://ai.google.dev/gemma/docs/core