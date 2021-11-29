Click {

	classvar <all, <loopCues;
	var <bpm, <beats, <beatDiv, <>pan, <>amp, <>out, <repeats;
	var name, barArray, <key, <>pattern;

	// add clock stuff! Something like:
	// clock = clock ? TempoClock.default;

	*initClass {

		all = IdentityDictionary();
		loopCues = IdentityDictionary();

		StartUp.add{

			SynthDef(\clickSynth,{
				var env = Env.perc(\atk.kr(0.01),\rls.kr(0.25),1.0,\curve.kr(-4)).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));
				sig = LPF.ar(sig,8000);
				sig = sig * env * \amp.kr(0.5);
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

		name = "%_%%%%".format(bpm,beats,subDiv,repeats,out).asSymbol;

		^name
	}

	prCreateBarArray {

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

	prMakePattern { |bar, name|
		var dur = 60 / (bpm * beatDiv);
		key = ("c" ++ name).asSymbol;

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq([ dur ],inf),
				\freq, Pseq(1000 * bar,repeats),
				\pan, Pfunc({ pan }),               // a bit hacky maybe? allows me to pass both/either floats and {bus.getSynchronous}...
				\amp, Pfunc({ amp.value }),         // must test if several Clicks will read from the same Bus.control = amp
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

	*clear { this.all.do.clear }  // check this??? Other examples use .copy.do....why?

	/*--- convenience method shit, must test ---*/
	asLoop { | cueKey |
		// something like:
		// remove key from Click.all, or is there a .replace method...also other cleanup stuff
		// ClickLoop(bpm, beats, beatDiv, repeats, cueKey, pan, amp, out); does that work?
	}

	addCue {} // or .asCue? get the bell to follow the barArray, whether it's an Env, Man, etc. possible?

}

ClickLoop : Click {

	var <cue;

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
		loopCues.removeAt(cue);
	}

	play { this.reset; this.pattern.play }

	stop { this.pattern.stop; this.reset }

	reset { loopCues.put(cue, true) }

	release { loopCues.put(cue, false) }

}

ClickEnv : Click {

	*new { |bpmStart = 60, bpmEnd = 120, beats = 1, beatDiv = 1, repeats = 1, dur = 4, curve = 'exp', pan = 0, amp = 0.5, out = 0|
		^super.newCopyArgs("%e%".format(bpmStart, bpmEnd), beats, beatDiv, pan, amp, out, repeats).initEnv(bpmStart, bpmEnd, dur, curve);
	}

	initEnv { |start, end, dur, curve|
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeEnvPat(barArray, name, start, end, dur, curve);

		all.put(key,pattern);
	}

	prMakeEnvPat { |bar, name, start, end, dur, curve|
		var tempi = 60 / ([ start, end ] * beatDiv);
		key = "%_%".format(name,dur.asString).asSymbol;

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseg(tempi,dur,curve,repeats),
				\freq,Pseq(1000 * bar,inf),
				\pan, Pfunc({ pan }),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		)
		^pattern
	}
}

ClickCue : Click {

	classvar bufnum;

	*initClass {

		StartUp.add{

			SynthDef(\clickCuePlayback,{
				var bufnum = \bufnum.kr();
				var sig = PlayBuf.ar(1,bufnum,BufRateScale.kr(bufnum),doneAction: 2);
				sig = sig * \amp.kr(0.5);
				OffsetOut.ar(\outBus.kr(0),sig);
			}).add;


			// load buffers here?
		}
	}

	prMakePattern { |bar, name|
		var dur = 60 / (bpm * beatDiv);
		var cueBar = this.prMakeCueBar(bar);

		//can evenutally make a Dictionary with several available sounds
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "Sounds" +/+ "cueBell.wav"; // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = ("q" ++ name).asSymbol;

		pattern = Pdef(key,

			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([dur],inf),
					\freq,Pseq(1000 * bar,repeats),
					\pan, Pfunc({ pan }),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq([dur],inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
					\pan, Pfunc({ pan }),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				)
			])
		)
		^pattern
	}

	prMakeCueBar { |clickBar|
		var cueBar;

		if(clickBar.size == 1,{
			cueBar = Array.fill(clickBar.size,{\note})
		},{
			cueBar = clickBar.collect({ |item, i|
				if(item == 2,{\note},{\rest})
			})
		});
		^cueBar
	}

	// setBuf { |newBuf|  } // Gotta figure this out...maybe select from already loaded buffers in the file? Dictionary?
}


ClickMan : Click {

	var beatArr;

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, pan = 0, amp = 0.5, out = 0|
		^super.newCopyArgs("man", bpmArray.size, beatDiv, pan, amp, out, repeats).manInit(bpmArray);
	}

	manInit { |bpmArray|
		beatArr = bpmArray;
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeManPat(barArray, name, bpmArray);

		all.put(key,pattern);
	}

	prMakeManPat { |bar, name|
		var dur = 60 / (beatArr.stutter(beatDiv) * beatDiv);
		key = name.asSymbol;

		pattern = Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq(dur.flat,inf),
				\freq, Pseq(1000 * bar,repeats),
				\pan, Pfunc({ pan }),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		);
		^pattern
	}

	/*asLoop { |cueName|
	var cue;
	var dur = 60 / (beatArr.stutter(beatDiv) * beatDiv);
	this.clear;
	key = ("m" ++ name).asSymbol;

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
	\dur, Pseq(dur.flat,inf),
	\freq, Pwhile({ loopCues.at(cue) }, Pseq(1000 * barArray,repeats)),
	\pan, Pfunc({ pan }),
	\amp, Pfunc({ amp }),
	\outBus, Pfunc({ out }),
	)
	)
	},{
	"loop must have finite length".throw;
	});

	all.put(key,pattern);
	loopCues.put(cue,true);
	^pattern
	}*/
}

// ClickCueMan : Click {}  ???????