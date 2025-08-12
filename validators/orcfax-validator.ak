use aiken/collection/list
use cardano/transaction.{InlineDatum, Output, OutputReference, Transaction}
use orcfax/rational.{RationalFsDat}

pub type RedeemerObject {
  num: Int,
  denom: Int,
  // Just as an example, you can add more fields if needed
  data: ByteArray,
}

pub type DatumObject {
  data: ByteArray,
}

validator orcfax_validator(feed: ByteArray) {
  spend(
    _datum: Option<Data>,
    redeemer: RedeemerObject,
    _utxo: OutputReference,
    tx: Transaction,
  ) {
    let Transaction { reference_inputs, .. } = tx
    // Reading the reference input from the transaction
    expect Some(ref_input) = list.head(reference_inputs)
    expect InlineDatum(inlinedatum) = ref_input.output.datum
    expect fs_dat: RationalFsDat = inlinedatum

    and {
      // The Validator only works for this specific feed
      fs_dat.statement.feed_id == feed,
      // Verify that the feed value is greater than the redeemer value
      fs_dat.statement.body.num * redeemer.denom > redeemer.num * fs_dat.statement.body.denom,
    }
  }

  else(_) {
    fail
  }
}
