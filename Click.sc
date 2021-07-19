Click {

	classvar <all, <loopCues;
	var <bpm, <beats, <beatDiv, <>pan, <>amp, <>out, <repeats;
	var name, barArray, <key, <>pattern;

	*initClass {

		all = IdentityDictionary.new;
		loopCues = IdentityDictionary.new;

		StartUp.add{

			SynthDef(\clickSynth,{
				var env = Env.perc(\atk.kr(0.01),\rls.kr(0.25),1.0,\curve.kr(-4)).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));
				sig = LPF.ar(sig,8000);
				sig = Pan2.ar(sig * env,\pan.kr(0),\amp.kr(0.5));
				OffsetOut.ar(\outBus.kr(0),sig);
			}).add;
		}
	}

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, pan = 0, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, pan, amp, out, repeats).init;
	}

	init {
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakePattern(barArray, name);

		all.put(key,pattern);
	}

	prGenerateKey {
		var subDiv = switch(beatDiv,
			1,"q", // quarter
			2,"e", // eighth
			3,"t", // triplet
			4,"s", // sixteenth
			5,"f", // quintuplet (fives)
			{"Mike's laziness doesn't curently support more than quintuplet subdivisions".postln}
		);

		name = "%_%%%".format(bpm,beats,subDiv,repeats).asSymbol;

		^name
	}

	prCreateBarArray {

		if(beats.isInteger.not || beatDiv.isInteger.not,{
			beats = beats.floor;
			beatDiv = beatDiv.floor;
			"beats and beatDiv must be integers - they've been floored".warn

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

	prMakePattern { |bar, name|
		var dur = 60 / (bpm * beatDiv);
		key = ("t" ++ name).asSymbol;

		// if repeats.isInteger or inf, etc. ?

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq([ dur ],inf),
				\freq, Pseq(1000 * bar,repeats),
				\pan, Pfunc({ pan }),
				\amp, Pfunc({ amp }),           // must test if several Clicks will read from the same Bus.control = amp
				\outBus, Pfunc({ out }),
			)
		);

		^pattern
	}

	play { this.pattern.play }

	stop { this.pattern.stop }

	clear {
		this.pattern.clear;
		all.removeAt(key)
	}

	/*--- loop shit, must test ---*/
	asLoop { | cueKey |
		// something like:
		// remove key from Click.all, or is there a .replace method...also other cleanup stuff
		// ClickLoop(bpm, beats, beatDiv, repeats, cueKey, pan, amp, out); does that work?
	}
}

ClickLoop : Click {

	var <>cue;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, loopKey = nil, pan = 0, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, pan, amp, out, repeats).initLoop(loopKey);
	}

	initLoop { | cueName |
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeLoop(barArray, name, cueName);

		all.put(key,pattern);
		loopCues.put(cue,true);
	}

	prMakeLoop { | bar, name, cueName |
		var dur = 60 / (bpm * beatDiv);
		key = ("l" ++ name).asSymbol;

		if(cueName.isNil,{
			"no loopKey assigned: using pattern key".warn;
			cue = key;

		},{
			cue = cueName.asSymbol;
		});

		if(repeats != inf,{
			pattern = Pdef(key,
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([ dur ],inf),
					\freq, Pwhile({ loopCues.at(cue) }, Pseq(1000 * bar,repeats)),
					\pan, Pfunc({ pan }),
					\amp, Pfunc({ amp }),
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
		loopCues.removeAt(cue);
	}

	play { this.pattern.play; this.reset }

	stop { this.pattern.stop; this.reset }

	reset { loopCues.put(cue, true); }

	release { loopCues.put(this.cue, false) }
}

ClickEnv : Click {

	// can pass in args to a Pseg? gotta figure this one out...


	prMakePattern { |bar, name|
		var dur = 60 / (this.bpm * beatDiv);
		key = "e" ++ name;

		pattern = Pdef(key.asSymbol,
			Pbind(
				\instrument, \clickSynth,
				// \dur, Pseg(levels,durs,curves,repeats), // ??
				\freq,Pseq(1000 * bar,repeats),
				\pan, Pfunc({pan}),
				\amp, Pfunc({amp}),
				\outBus, Pfunc({out}),
			)
		)

		^pattern
	}

}


ClickCue : Click {

	var bufnum;

	*initClass {

		// Buffer.read(); //don't uncomment this unless it works - it impedes the interpreter booting process!

		StartUp.add{

			SynthDef(\clickCuePlayback, {
				var bufnum = \bufnum.kr();
				var sig = PlayBuf.ar(1,bufnum,BufRateScale.kr(bufnum) * \rate.kr(1),doneAction: 2);
				sig = Pan2.ar(sig,\pan.kr(0),\amp.kr(0.5));
				OffsetOut.ar(\outBus.kr(0),sig);
			}).add;
		}
	}

	// maybe overwrite this one?
	init {
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakePattern(barArray, name);

		all.put(key,pattern);
	}

	// prCreateBarArray {} // create new function maybe? just for the cue click?

	prMakePattern { |bar, name|
		var dur = 60 / (this.bpm * beatDiv);
		var cueBar = this.prMakeCueBar(bar);
		key = "c" ++ name;

		pattern = Pdef(key.asSymbol,

			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([dur],inf),
					\freq,Pseq(1000 * bar,repeats),
					\amp, Pfunc({amp}),
					\outBus, Pfunc({out}),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq(cueBar,inf),
					\bufnum, Pseq([bufnum],repeats),
					\amp, Pfunc({ amp }),
					\outBus, Pfunc({ out }),
				)
			])
		)

		^pattern
	}

	/*prMakeCueBar { |clickBar| //[ 2, 1, 1.5, 1, 1.5, 1, 1.5, 1 ] and look for 2s, for example...but Click(60,1,1) gives [1]!

	// clickBar.reshape -> separate at 2s, etc.

	^cueBar
	}*/

	// -must check -> some of the clicks have a long count in, some are shorter...make two separate methods? Boolean? two different Classes?
	// -or just make this have more flexible arguments -> pass array for bell rhythms?


	setBuf {} // class method
}

/*
ClickMan : Click {

// manual input of barArray and somehow durArray!! This is for asymmetric Clicks (the 11/8 riff, for example)

}
*/
