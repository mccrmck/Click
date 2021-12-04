/*
AbstractClick { // this might have to get added to the top of the chain at some point in order to fix some inheritance issues...

all = IdentityDictionary();
loopCues = IdentityDictionary();

maybe the SynthDefs get loaded here as well???
change repeats args to reps! fewer keystrokes..

this needs to be reworked - the init functions keep fucking things up!


}
*/

Click {

	classvar <all, <loopCues;
	var <bpm, <beats, <beatDiv, <>amp, <>out, <repeats;
	var name, <barArray, <key, <>pattern;

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

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, amp, out, repeats).init;
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

		name = "%_%%%o%".format(bpm,beats,subDiv,repeats,out).asSymbol;

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
				\freq, Pseq(1000 * bar,repeats),     // a bit hacky maybe? allows me to pass both/either floats and {bus.getSynchronous}...
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
		// ClickLoop(bpm, beats, beatDiv, repeats, cueKey, amp, out); does that work?
	}

	addCue {} // or .asCue? get the bell to follow the barArray, whether it's an Env, Man, etc. possible?

}

ClickLoop : Click {

	var <cue;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, loopKey = nil, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, amp, out, repeats).initLoop(loopKey);
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

	*new { |bpmStart = 60, bpmEnd = 120, beats = 1, beatDiv = 1, repeats = 1, dur = 4, curve = 'exp', amp = 0.5, out = 0|
		^super.newCopyArgs("%e%".format(bpmStart, bpmEnd), beats, beatDiv, amp, out, repeats).initEnv(bpmStart, bpmEnd, dur, curve);
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
		}
	}

	prMakePattern { |bar, name|
		var dur = 60 / (bpm * beatDiv);
		var cueBar = this.prMakeCueBar(bar);

		//can evenutally make a Dictionary with several available sounds
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "Sounds" +/+ "cueBell.wav";                       // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = ("q" ++ name).asSymbol;

		pattern = Pdef(key,

			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([dur],inf),
					\freq,Pseq(1000 * bar,repeats),
					\amp, Pfunc({ amp.value }),
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq([dur],inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
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
			cueBar = [\note]
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

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("man", bpmArray.size, beatDiv, amp, out, repeats).manInit(bpmArray);
	}

	manInit { |bpmArray|
		beatArr = bpmArray;
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeManPat(barArray, name, bpmArray);   // check this shit - this method only takes three args????

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

ClickConCat : Click {                       // this should evenutally inherit from AbstractClick!

	var <pattern;

	*new { | repeats ...clicks |
		^super.new.init2(repeats,clicks);  // this calls Click(), generating a click at every instance call...
	}

	init2 { | repeats, clicks |
		var array = clicks.asArray.flat.clickKeys;
		var newKey = "cc%".format(repeats.asString);
		array.do({ |key| newKey = newKey ++ key.asString});
		newKey = newKey.removeEvery("_").asSymbol;

		pattern = Pdef(newKey,
			Psym(
				Pseq(array,repeats)
			)
		);

		all.put(newKey,pattern);

		^pattern
	}

	// ++ {} // can I make this a shortcut method (w/ default repeat = 1) w/o adding it to the inherited classes?
}

ClickConCatCue : ClickConCat {

	var <pattern;

	*new { | repeats ...clicks |
		^super.new.init2(repeats,clicks);  // this calls Click(), generating a click at every instance call...
	}

	init2 { | repeats, clicks |
		var cueBar = this.prConCatCueBar(clicks);                      // doesn't work yet!!


		var array = clicks.asArray.flat.clickKeys;
		var newKey = "cq%".format(repeats.asString);
		array.do({ |key| newKey = newKey ++ key.asString});
		newKey = newKey.removeEvery("_").asSymbol;

		//prevent CLickLoops!


		/*
		pattern = Pdef(newKey,
		Ppar([
		Psym(
		Pseq(array,repeats)
		),
		//cue bar goes here
		])

		);

		all.put(newKey,pattern);

		^pattern
		*/
	}

	prConCatCueBar { |clicks|
	}

}

ClickConCatLoop : ClickConCat {

	var <pattern, <cue;

	*new { | loopKey ...clicks |
		^super.new.init2loop(loopKey,clicks);  // this calls Click(), generating a click at every instance call...
	}

	init2loop { | loopKey, clicks |
		var array = clicks.asArray.flat.clickKeys;
		var newKey = "cl";
		array.do({ |key| newKey = newKey ++ key.asString});
		newKey = newKey.removeEvery("_").asSymbol;

		if(loopKey.isNil,{
			"no loopKey assigned: using pattern key".warn;
			cue = newKey;
		},{
			cue = loopKey.asSymbol;
		});

		clicks.do({ |clk|
			if(clk.isKindOf(ClickLoop),{"cannot loop a loop! Or can I?".throw})
		});

		array.postln;
		pattern = Pdef(newKey,
			Pwhile({ loopCues.at(cue) },
				Psym(Pseq(array))
			)
		);

		all.put(newKey,pattern);
		loopCues.put(cue,true);

		^pattern
	}
}

// a = ClickConCatLoop(\test,ClickMan([1,4/3,1] * 100,1,8),Click(200,4))


