+ SequenceableCollection {

	clickKeys {
		^this.deepCollect(3,{ |click| click.key })
	}
}