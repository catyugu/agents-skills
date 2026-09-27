# Results prose: claims must match the evidence

Rules for the parts of a report that report measurements — abstract, results, discussion, conclusion.
Reviewers reject these faster than they reject weak numbers.

## 1. Claim only what the data supports

- Two data points whose size **and** geometry both changed do not establish scaling behaviour. Report
  the ratios and say that a fixed-model refinement study is still needed.
- A metric that merely did not fire is not evidence of good tuning: zero rejected time steps do not
  show the step controller was not over-conservative. State the step count and that no step was
  rejected; stop there.
- A code path the cases never exercised is a limitation, not a "standby capability". Say the cases used
  constant properties, that the nonlinear path was therefore not triggered, and that only its
  linear-limit correctness was verified.
- Attribute an error increase to a mechanism only after a separation experiment. Without one, write
  that the extra error is *associated with* the transient time stepping and solver settings.
- A quantity named after a physical model must match it: `∇·(σ∇V) = 0` is steady conduction
  (稳恒导电场), not electrostatics; "fully coupled" only when the discretization is monolithic.

## 2. Terminology that reviewers catch

- "Machine precision" means double-precision epsilon (about `1e-16`). A relative agreement of `1e-8`
  is "consistent to the `1e-8` level", not machine precision.
- A speedup is a ratio, not a multiple of the *time*: "求解耗时是 COMSOL 的 1.5 倍" reads as slower
  than the reference. Write the speedup (`1.52×`) or the time ratio (`0.66×`), and keep the English
  abstract identical in direction and value.
- One rounding convention for a speedup across abstract, results and conclusion.
- A keyword names the study's core, not every mechanism in it; a technique that occupies one section
  is usually not a keyword.

## 3. Abstract and conclusion

- An abstract is not a table of contents. Do not enumerate every capability the library has (basis
  families, mappings, assembly strategies); state the problem, the method, and the outcome
  *qualitatively*. No measured value belongs in it — no error magnitude, speedup factor, iteration,
  step or peak count. "Accuracy meets the stated acceptance criteria" and "the coupled transient cases
  are solved more efficiently than the reference" carry the same information without exposing numbers
  the reader has no method for yet; the figures live in the results section, and the English abstract
  must repeat the same direction with no number either.
- The conclusion reports what was done, what the numbers were, and what remains unverified. It does not
  restate each section as "第一…第二…第三…", and it does not claim what the results cannot support.

## 4. Compression that removes the generated-text flavour

- Replace "其一…其二…其三" enumerations with one sentence carrying the same content.
- Delete the "why this is good" sentence that follows a fact. Stating the fact plainly is the report's
  job; praising it is what makes text read as machine-written.
- Delete textbook definitions of methods the audience knows (preconditioner families, Krylov variants);
  keep the measured comparison that justified the choice.
- Keep the research content, cut the explanation around it: one governing equation, one
  boundary-condition sentence, one weak form, one implementation sentence per physics field.
- A results section for a case reads "model and purpose → what is special in the boundary or coupling
  settings → numerical result and error". Uniform structure across cases beats narrating each figure.
