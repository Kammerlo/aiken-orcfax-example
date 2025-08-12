/// usr/bin/env jbang "$0" "$@" ; exit $?
///
// @formatter:off
//JAVA 24+
//COMPILE_OPTIONS -source 24 -Xlint:none
//RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED

//DEPS com.bloxbean.cardano:cardano-client-lib:0.7.0-beta2
//DEPS com.bloxbean.cardano:cardano-client-backend-blockfrost:0.7.0-beta2
//DEPS com.bloxbean.cardano:aiken-java-binding:0.1.0
//DEPS org.apache.commons:commons-math3:3.6.1
// @formatter:on

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.apache.commons.math3.fraction.Fraction;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.util.HexUtil;

public class OrcfaxTest {

        static String oracleScriptAddress = "addr_test1vr6lx0dk534vvv93js4n0qnqs0y9mkxtursdwc7ed7szeqgur3u54";
        static String oracleUtxoAddress = "addr_test1wrnv3gc5462zgqtpj3s0qrrfmc73hxtdkkydgppzgwjtykg9t3l2f";
        static String orcfaxFeed = "CER/ADA-USD/3";

        static double minAdaValue = 0.7d;
        static double minAdaInScript = 2d;

        static BackendService backendService = new BFBackendService("https://cardano-preview.blockfrost.io/api/v0/",
                        "previewYOUR_API_KEY_HERE");
        static QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        static UtxoService utxoService = backendService.getUtxoService();

        static String mnemonic = "word1 word2 word3 ... word24";

        // The network used for this example is Testnet
        static Network network = Networks.preview();
        static PlutusScript plutusScript = getParametrisedPlutusScript();
        static Address scriptAddress = AddressProvider.getEntAddress(plutusScript, network);

        static Account initiator = Account.createFromMnemonic(network, mnemonic);

        public static void main(String... args) throws Exception {
                // Account account = new Account(network);
                System.out.println("Account address: " + initiator.baseAddress());
                // System.out.print("Mnemonic: " + account.mnemonic());

                // Initialize the Orcfax client
                System.out.println("Initializing Orcfax client...");
                System.out.println("Using oracle script address: " + oracleScriptAddress);
                System.out.println("Using oracle UTxO address: " + oracleUtxoAddress);

                // Get the reference UTxO
                Optional<Utxo> referenceUtxoOptional = getReferenceUtxo();
                if (referenceUtxoOptional.isEmpty()) {
                        System.out.println("No reference UTxO found for the specified feed: " + orcfaxFeed);
                        return;
                }
                Utxo referenceUtxo = referenceUtxoOptional.get();
                System.out.println("Using reference UTxO: " + referenceUtxo.getTxHash() + "#"
                                + referenceUtxo.getOutputIndex());

                if (isTopUpNeeded()) {
                        topUpScript();
                }
                unlockUtxo(referenceUtxo);
        }

        private static void unlockUtxo(Utxo referenceUtxo) {
                DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
                List<Utxo> utxos = utxoSupplier.getAll(scriptAddress.getAddress());
                // utxos = utxos.stream().filter(u -> u.getInlineDatum() == null).toList();
                if (utxos.isEmpty()) {
                        System.out.println("No UTxOs found for the script address: " + scriptAddress.getAddress());
                        return;
                }
                System.out.println("Found " + utxos.size() + " UTxOs for the script address: "
                                + scriptAddress.getAddress() + " - with amount: " + utxos.getFirst().getAmount());
                Fraction fraction = new Fraction(minAdaValue);
                ConstrPlutusData redeemer = ConstrPlutusData.builder()
                                .alternative(0)
                                .data(ListPlutusData.of(BigIntPlutusData.of(fraction.getNumerator()),
                                                BigIntPlutusData.of(fraction.getDenominator()),
                                                BytesPlutusData.of(orcfaxFeed)))
                                .build();

                ScriptTx scriptTx = new ScriptTx()
                                .readFrom(referenceUtxo.getTxHash(), referenceUtxo.getOutputIndex())
                                .collectFrom(utxos,
                                                redeemer)
                                .payToContract(scriptAddress.getAddress(), Amount.ada(2),
                                                BytesPlutusData.of(redeemer.getDatumHash()))
                                .attachSpendingValidator(plutusScript)
                                .withChangeAddress(scriptAddress.getAddress());
                System.out.println("Initiator address: " + initiator.baseAddress());
                TxResult completeAndWait = quickTxBuilder.compose(scriptTx)
                                .collateralPayer(initiator.baseAddress())
                                .withSigner(SignerProviders.signerFrom(initiator))
                                .feePayer(initiator.baseAddress())
                                .completeAndWait();
                System.out.println("Transaction completed: " + completeAndWait.getTxHash());
        }

        private static boolean isTopUpNeeded() {
                DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
                List<Utxo> utxos = utxoSupplier.getAll(scriptAddress.getAddress());
                if (utxos.isEmpty()) {
                        System.out.println("No UTxOs found for the script address: " + scriptAddress.getAddress());
                        return true;
                }
                long totalAda = utxos.stream().flatMap(u -> u.getAmount().stream())
                                .filter(amount -> amount.getUnit().equals("lovelace"))
                                .mapToLong(a -> a.getQuantity().longValue()).sum() / 1_000_000; // Convert lovelace to
                                                                                                // ADA
                System.out.println("Total ADA in script address: " + totalAda);
                if (totalAda < minAdaInScript) {
                        System.out.println("Top up needed for the script address: " + scriptAddress.getAddress());
                        return true;
                } else {
                        System.out.println("No top up needed for the script address: " + scriptAddress.getAddress());
                        return false;
                }
        }

        private static void topUpScript() {
                System.out.println("Top up script address: " + scriptAddress.getAddress());
                Tx tx = new Tx()
                                .payToAddress(scriptAddress.getAddress(), Amount.ada(2))
                                .from(initiator.baseAddress())
                                .withChangeAddress(initiator.baseAddress());
                TxResult completeAndWait = quickTxBuilder.compose(tx)
                                .withSigner(SignerProviders.signerFrom(initiator))

                                .feePayer(initiator.baseAddress())
                                .completeAndWait();
                System.out.println(
                                "Script address topped up with 10 ADA - Transaction: " + completeAndWait.getTxHash());
        }

        private static Optional<Utxo> getReferenceUtxo() {
                DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
                List<Utxo> utxoAddressList = utxoSupplier.getAll(oracleUtxoAddress);

                Optional<Utxo> referenceInput = Optional.empty();
                for (Utxo utxo : utxoAddressList) {
                        try {
                                String inlineDatum = utxo.getInlineDatum();
                                ConstrPlutusData deserialize = (ConstrPlutusData) PlutusData
                                                .deserialize(HexUtil.decodeHexString(inlineDatum));
                                ListPlutusData data = deserialize.getData();
                                ConstrPlutusData listContent = (ConstrPlutusData) data.getPlutusDataList().getFirst();
                                BytesPlutusData bytesContent = (BytesPlutusData) listContent.getData()
                                                .getPlutusDataList().getFirst();
                                String feedTitleString = new String(bytesContent.getValue());
                                System.out.println("Feed title: " + feedTitleString + " - TxHash: " + utxo.getTxHash()
                                                + " - Output Index: " + utxo.getOutputIndex());
                                if (feedTitleString.equals(orcfaxFeed)) {
                                        referenceInput = Optional.of(utxo);
                                        ConstrPlutusData value = (ConstrPlutusData) listContent.getData()
                                                        .getPlutusDataList().get(2);
                                        List<PlutusData> plutusDataList = value.getData().getPlutusDataList();
                                        BigIntPlutusData num = (BigIntPlutusData) plutusDataList.getFirst();
                                        BigIntPlutusData denom = (BigIntPlutusData) plutusDataList.getLast();
                                        Double rationalValue = num.getValue().doubleValue()
                                                        / denom.getValue().doubleValue();
                                        System.out.println("Rational value: " + rationalValue);
                                        break;
                                }
                        } catch (Exception e) {
                                // System.out.println("Error processing UTxO: " + utxo.getTxHash() + "#"
                                // + utxo.getOutputIndex());
                        }
                }
                return referenceInput;
        }

        private static PlutusScript getParametrisedPlutusScript() {
                PlutusContractBlueprint plutusContractBlueprint = PlutusBlueprintLoader
                                .loadBlueprint(new File("./plutus.json"));
                String orcfaxCompiledCode = plutusContractBlueprint.getValidators().getFirst()
                                .getCompiledCode();

                // Apply parameters to the validator compiled code to get the compiled code
                String compiledCode = AikenScriptUtil.applyParamToScript(ListPlutusData.of(
                                BytesPlutusData.of(orcfaxFeed)),
                                orcfaxCompiledCode);

                PlutusScript plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode,
                                PlutusVersion.v3);
                return plutusScript;
        }
}