# Orcfax Oracle Aiken Smart Contract Example

This repository provides a working example of how to use an [Orcfax](https://orcfax.io/) oracle feed within a custom Aiken smart contract on the Cardano blockchain. It includes:

1.  An **Aiken smart contract** (`orcfax_validator.ak`) that is parameterized with an Orcfax feed ID.
2.  A **Java example** (`OrcfaxTest.java`) using [cardano-client-lib](https://github.com/bloxbean/cardano-client-lib) to interact with the smart contract on the Preview testnet.

The validator's logic is simple: it allows spending a UTXO only if the value from the specified Orcfax feed is greater than a value provided by the user in the redeemer. This demonstrates a common pattern of using oracle data to trigger on-chain actions.

---

## 🚀 How It Works

### Smart Contract Logic

The Aiken validator (`orcfax_validator`) is designed to secure UTxOs at its address. To unlock a UTXO, a transaction must:

1.  **Provide an Orcfax UTXO as a reference input**. This UTXO contains the oracle's data (e.g., the price of ADA/USD) in its inline datum.
2.  **Ensure the reference input's feed ID matches** the one the contract was parameterized with. This prevents using data from an incorrect oracle feed.
3.  **Provide a redeemer** containing a rational number (numerator and denominator).
4.  **Satisfy the core condition**: The oracle's value must be strictly greater than the value in the redeemer. The contract uses integer cross-multiplication to avoid floating-point arithmetic on-chain:

    $$
    \frac{\text{Oracle Numerator}}{\text{Oracle Denominator}} > \frac{\text{Redeemer Numerator}}{\text{Redeemer Denominator}} \implies \text{Oracle Num} \times \text{Redeemer Denom} > \text{Redeemer Num} \times \text{Oracle Denom}
    $$

### Java Client Script

The `OrcfaxTest.java` script automates the process of interacting with the smart contract. It performs the following steps:

1.  **Loads the compiled Aiken contract** (`plutus.json`).
2.  **Applies the parameter** (the `orcfaxFeed` ID) to the contract's compiled code to generate the final script and its address.
3.  **Finds the correct Orcfax reference UTXO** on the Preview testnet by querying the known Orcfax address and parsing the inline datums.
4.  **Manages funds** at the script address, topping it up if its balance is too low.
5.  **Builds and submits the transaction** to unlock funds from the script, providing the Orcfax UTXO as a reference input and constructing the necessary redeemer.

---

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

* [**Aiken**](https://aiken-lang.org/installation-guide): The toolchain for compiling the smart contract.
* [**JBang**](https://www.jbang.dev/download/): A tool to easily run Java scripts.
* **Java 24+**: The Java example script requires JDK 24 or newer.
* **Blockfrost API Key**: Get a free API key for the **Cardano Preview Testnet** from [Blockfrost.io](https://blockfrost.io).
* **Preview Testnet Wallet**: A Cardano wallet with some test ADA (`tADA`) on the Preview network. You can get `tADA` from the official [Cardano Testnet Faucet](https://docs.cardano.org/cardano-testnets/tools/faucet/).

---

## 🛠️ Setup and Execution

Follow these steps to compile the contract and run the Java client.

### 1. Compile the Aiken Smart Contract

First, compile the Aiken code. This will validate the code and generate a `plutus.json` file in the project's root directory, which contains the compiled contract blueprint.

From your terminal, run:

```sh
aiken build
```

### 2. Configure the Java script

Open the `OrcfaxTest.java` file and update the following variables with your own details:
```java
public class OrcfaxTest {

    // ...

    // Step 2.1: Add your Blockfrost Preview API key
    static BackendService backendService = new BFBackendService("https://cardano-preview.blockfrost.io/api/v0/",
                    "previewYOUR_API_KEY_HERE");

    // ...

    // Step 2.2: Add the 24-word mnemonic for your Preview testnet wallet
    static String mnemonic = "word1 word2 word3 ... word24";

    // ...
}
```
You can also change the `orcfaxFeed` or the `minAdaValue` to test different scenarios.

### 3. Run the example
With the `plutus.json` file in place and the Java script configure, execute the scrupt using JBang:
```sh
jbang ccl-offchain/OrcfaxTest.java
```
The script will print it's progress to the console, including the addresses it's using, the reference UTxO it found, and the final transaction ID upon success.