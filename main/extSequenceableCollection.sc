+ SequenceableCollection {

	clickKeys {
		^this.deepCollect(3,{ |click|
			if(click.isKindOf(Click),{
				click.key
			},{
				click
			})
		})
	}
}