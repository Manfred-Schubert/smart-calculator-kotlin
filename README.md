# Smart Calculator (Kotlin)

A robust, arbitrary-precision command-line calculator built using modern compiler-design principles.


This application was developed as a milestone project within the **Hyperskill / JetBrains Academy** Kotlin Core curriculum.

- **Course Project Page:** https://hyperskill.org/projects/88
- **My Hyperskill Profile:** https://hyperskill.org/profile/629496713

---

## Key Features
- **Lexical Analysis:** Built a custom stateful `Tokenizer` utilizing regular expressions to cleanly parse raw infix mathematical strings.
- **Type-Safe Domain Modeling:** Leveraged Kotlin `sealed class` architectures and data objects to represent tokens and exceptions safely at compile-time.
- **Shunting-Yard Algorithm:** Implemented Dijkstra's Shunting-Yard algorithm using an `ArrayDeque` stack to seamlessly convert Infix expressions to Postfix (RPN) notation.
- **Arbitrary Precision:** Built completely atop Java's `BigInteger` to support infinitely large numbers and prevent overflow runtime bugs.
- **Smart Sign Resolution:** Dynamically processes and normalizes complex chains of unary operators (e.g., resolving `-5 + (---3 * 2)` accurately).

## Tech Stack
- Kotlin (JVM)
- Java Math API (`BigInteger`)

## How to Run
1. Open the project root folder in IntelliJ IDEA.
2. Run the `main()` function inside `Calculator.kt`.
3. Type in `/help` for instructions.