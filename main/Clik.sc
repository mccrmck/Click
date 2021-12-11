AbstractClick {
	classvar <all, <loopCues;
	var	<bpm, <beats, <beatDiv, <repeats, <amp, <out;
	var <key, <pattern;

	// add clock stuff..here?! Something like:
	// clock = clock ? TempoClock.default;

	*initClass{
		all = IdentityDictionary();
		loopCues = IdentityDictionary();

		StartUp.add{

			SynthDef(\clickSynth,{
				var env = Env.perc(\atk.kr(0.01),\rls.kr(0.25),1.0,\curve.kr(-4)).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));
				sig = LPF.ar(sig,8000);
				sig = sig * env * \amp.kr(0.5);
				OffsetOut.ar(\outBus.kr(),sig);
			}).add;

			SynthDef(\clickCuePlayback,{
				var bufnum = \bufnum.kr();
				var sig = PlayBuf.ar(1,bufnum,BufRateScale.kr(bufnum),doneAction: 2);
				sig = sig * \amp.kr(0.5);
				OffsetOut.ar(\outBus.kr(0),sig);
			}).add;
		}
	}

	makePrefix {
		var subDiv = switch(beatDiv,
			1,"q", // quarter
			2,"e", // eighth
			3,"t", // triplet
			4,"s", // sixteenth
			5,"f", // quintuplet (fives)
			{ "Mike's laziness doesn't curently support more than quintuplet subdivisions".postln }
		);

		var prefix = "%_%%%o%".format(bpm,beats,subDiv,repeats,out).asSymbol;

		^prefix
	}

	makeBarArray {
		var barArray;

		if(beats.isInteger.not || beatDiv.isInteger.not,{
			beats = beats.floor;
			beatDiv = beatDiv.floor;
			"beats and beatDiv must be integers - they've been floored".warn;
		});

		if(beatDiv > 1,{
			var subDiv = Array.fill(beatDiv,{1});
			subDiv[0] = 1.5;
			barArray = Array.fill(beats,subDiv);
			barArray = barArray.flat;
			barArray[0] = 2;
		},{
			if(beats == 1,{
				barArray = [1]
			},{
				barArray = Array.fill(beats,{1});
				barArray[0] = 2;
			})
		});
		^barArray
	}

	play { this.pattern.play }

	stop { this.pattern.stop }

	clear {
		this.pattern.clear;
		all.removeAt(key)
	}

	*clear { this.all.do.clear }  // check this??? Other examples use .copy.do....why?

}

Clik : AbstractClick {

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).init;
	}

	init {
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray|
		var dur = 60 / (bpm * beatDiv);
		key = ("c" ++ prefix).asSymbol;

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq([ dur ],inf),
				\freq, Pseq(1000 * barArray,repeats),     // a bit hacky maybe? allows me to pass both/either floats and {bus.getSynchronous}...
				\amp, Pfunc({ amp.value }),         // must test if several Clicks will read from the same Bus.control = amp
				\outBus, Pfunc({ out }),
			)
		);
		^pattern
	}

}

ClikLoop : AbstractClick {
	var <loopCue;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0, loopKey = nil|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).init(loopKey);

	}

	init { |loopKey|
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray, loopKey);

		all.put(key,pattern);
		loopCues.put(loopCue,true);
	}

	makePattern { |prefix, barArray, loopKey|
		var dur = 60 / (bpm * beatDiv);
		key = ("l" ++ prefix).asSymbol;

		if(loopKey.isNil,{
			"no loopKey assigned: using pattern key".warn;
			loopCue = key;
		},{
			loopCue = loopKey.asSymbol;
		});

		if(repeats != inf,{
			pattern = Pdef(key,
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([ dur ],inf),
					\freq, Pwhile({ loopCues.at(loopCue) }, Pseq(1000 * barArray,repeats)),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				)
			)
		},{
			"loop must have finite length".throw;
		});
		^pattern
	}

	clear {
		this.pattern.clear;
		all.removeAt(key);
		loopCues.removeAt(loopCue);
	}

	play { this.reset; this.pattern.play }

	stop { this.pattern.stop; this.reset }

	reset { loopCues.put(loopCue, true) }

	release { loopCues.put(loopCue, false) }
}

ClikEnv : AbstractClick {  // this has to be rethought a bit...I don't think the beats argument has any influence over the final outcome...

	*new { |bpmStartEnd = ([60,120]), beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0, dur = 4, curve = 'exp'|
		if( bpmStartEnd.isArray.not or: {bpmStartEnd.size != 2},{"bpmStartEnd must be an Array of 2 values".throw} );
		^super.newCopyArgs("%e%".format(bpmStartEnd[0], bpmStartEnd[1]), beats, beatDiv, repeats, amp, out).init(bpmStartEnd, dur, curve);
	}

	init { |bpmStartEnd, dur, curve|
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray, bpmStartEnd[0], bpmStartEnd[1], dur, curve);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray, start, end, dur, curve|
		var tempi = 60 / ([ start, end ] * beatDiv);
		key = "%_%".format(prefix,dur.asString).asSymbol;              // this is weird, no?

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseg(tempi, dur, curve, repeats),
				\freq,Pseq(1000 * barArray,inf),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		)
		^pattern
	}
}

ClikCue : AbstractClick {

	classvar bufnum;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).init;
	}

	init {
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray|
		var dur = 60 / (bpm * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		//can evenutally make a Dictionary with several available sounds
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "Sounds" +/+ "cueBell.wav";                   // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = ("q" ++ prefix).asSymbol;

		pattern = Pdef(key,
			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([ dur ],inf),
					\freq,Pseq(1000 * barArray,repeats),
					\amp, Pfunc({ amp.value }) / 2,
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq([ dur ],inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
					\amp, Pfunc({ amp.value }) / 2,
					\outBus, Pfunc({ out }),
				)
			])
		)
		^pattern
	}

	makeCuebar { |clickBar|
		var cueBar;

		if(clickBar.size == 1,{
			cueBar = [\note]
		},{
			cueBar = clickBar.collect({ |item|
				if(item == 2,{\note},{\rest})
			})
		});
		^cueBar
	}
}

ClikMan : AbstractClick {

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("man", bpmArray.size, beatDiv, repeats, amp, out).init(bpmArray);

		//consider fixing the bpmArg..."man" could produce duplicates!
	}

	init { |bpmArray|
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray, bpmArray);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray, bpmArray|
		var dur = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
		key = prefix.asSymbol;                                              // not exactly waterproof, I realize now...

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq(dur.flat,inf),
				\freq, Pseq(1000 * barArray,repeats),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		);
		^pattern
	}
}

ClikManCue : AbstractClick {                            // I could be wrong, but I think this can inherit from ClickMan w/ overwritten makePattern method??
	classvar bufnum;

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("manQ", bpmArray.size, beatDiv, repeats, amp, out).init(bpmArray);

		//consider fixing the bpmArg..."manCue" could produce duplicates!
	}

	init { |bpmArray|
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray, bpmArray);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray, bpmArray|
		var dur = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		//can evenutally make a Dictionary with several available sounds
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "Sounds" +/+ "cueBell.wav";            // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = prefix.asSymbol;

		pattern = Pdef(key,
			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq(dur.flat,inf),
					\freq, Pseq(1000 * barArray,repeats),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq(dur.flat,inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				)
			])
		);
		^pattern
	}

	makeCueBar { |clickBar|
		var cueBar;

		if(clickBar.size == 1,{
			cueBar = [\note]
		},{
			cueBar = clickBar.collect({ |item, i|
				if(item == 2,{\note},{\rest})
			})
		});
		^cueBar
	}
}





// to work with the countIn system, I think bpm would have to get the bpm for the first click in the clickArray?
ClikConCat : AbstractClick {    // consider inheriting from Object instead of AbstractClick...what is gained/lost??

	var <clickArray;
	var clickKeysArray;


	*new { |repeats ...clicks|
		^super.new.init(repeats, clicks);
	}

	init { |repeats, clicks|
		clickArray = clicks.asArray.flat;                   // this flattens things...but maybe it could also handle parallel patterns??? Could be nice..
		clickKeysArray = clickArray.clickKeys;
		this.makeConCatKey;
		pattern = this.makePattern(key,repeats);

		all.put(key,pattern);
	}

	/*makeConCatKey { | repeats |
		var newKey = "cc%".format(repeats.asString);
		clickKeysArray.do({ |key| newKey = newKey ++ key.asString});   // get away from working with clickKeys - see below
		key = newKey.removeEvery("_").asSymbol;

		^conCatKey
	}*/



	makePattern { |key, repeats|


		pattern = Pdef(key,
			Psym(
				Pseq(clickKeysArray,repeats)                 // after generating a unique key, this can just be a Pseq of Pdefs...no Psym shit
			)
		);
		^pattern
	}










	bpm {^this}
	beats {^this}
	beatDiv {^this}
	repeats {^repeats}

	amp_ { |val|
		/*this.clickArray.do({ |clk|
		clk.amp = val;
		});*/

		^this
	}

	out_ { |val|
		/*this.clickArray.do({ |clk|
		clk.out = val;
		});*/

		^this
	}

}














