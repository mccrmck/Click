/*
AbstractClick { // this might have to get added to the top of the chain at some point in order to fix some inheritance issues...

all = IdentityDictionary();
loopCues = IdentityDictionary();

maybe the SynthDefs get loaded here as well???
change repeats args to reps! fewer keystrokes..

this needs to be reworked - the init functions keep fucking things up!


generateKey {}

createBarArray {}

makePattern {}

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

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("man", bpmArray.size, beatDiv, amp, out, repeats).manInit(bpmArray);
	}

	manInit { |bpmArray|
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeManPat(barArray, name, bpmArray);

		all.put(key,pattern);
	}

	prMakeManPat { |bar, name, bpmArray|
		var dur = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
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
}

ClickManCue : Click {

	var <cue;

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("manCue", bpmArray.size, beatDiv, amp, out, repeats).manCueInit(bpmArray);
	}

	manCueInit { |bpmArray|
		this.prGenerateKey;
		this.prCreateBarArray;
		this.prMakeManPat(barArray, name, bpmArray);

		all.put(key,pattern);
	}

	prMakeManPat { |bar, name, bpmArray|
		var dur = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
		var cueBar = this.prMakeCueBar(bar);

		//can evenutally make a Dictionary with several available sounds
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "Sounds" +/+ "cueBell.wav";            // this seems a bit messy, no?
		var bufnum = Buffer.read(Server.default,path);

		key = name.asSymbol;

		pattern = Pdef(key,
			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq(dur.flat,inf),
					\freq, Pseq(1000 * bar,repeats),
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
}

/*----------------------------------------------------------------------------------------------*/

ClickConCat : Click {                       // this should evenutally inherit from AbstractClick!

	var <conCatPat;
	var clickArray, name;

	*new { | repeats ...clicks |
		^super.new.init2(repeats,clicks);  // this calls Click(), generating a click at every instance call...must rework this entire collection of classes
	}

	init2 { | repeats, clicks |
		clickArray = clicks.asArray.flat.clickKeys;
		this.prNewKey(repeats);
		this.prConCatPat(name,repeats);

		all.put(name,pattern);
	}

	prNewKey { | repeats |
		var newKey = "cc%".format(repeats.asString);
		clickArray.do({ |key| newKey = newKey ++ key.asString});
		name = newKey.removeEvery("_").asSymbol;

		^name
	}

	prConCatPat { |name, repeats|
		conCatPat = Pdef(name,
			Psym(
				Pseq(clickArray,repeats)
			)
		);

		^conCatPat
	}

	play { this.conCatPat.play }

	stop { this.conCatPat.stop }


	// HEEEEEEERE........and add these methods to ClickConCatLoop also!!
	amp_ { |val| ^this}

	out_ { |val| ^this}







	// ++ {} // can I make this a shortcut method (w/ default repeat = 1) w/o adding it to the inherited classes?
}

ClickConCatLoop : Click {

	var <conCatPat, <conCatCue;
	var clickArray, name;

	*new { | loopKey ...clicks |
		^super.new.init2(loopKey,clicks);  // this calls Click(), generating a click at every instance call...
	}

	init2 { | loopKey, clicks |
		clickArray = clicks.asArray.flat.clickKeys;
		this.prNewKeyAndCue(loopKey);
		this.prConCatPat(conCatCue,repeats);

		all.put(name,pattern);
	}

	prNewKeyAndCue { | loopKey |
		var newKey = "cl";
		clickArray.do({ |key| newKey = newKey ++ key.asString});
		name = newKey.removeEvery("_").asSymbol;

		if(loopKey.isNil,{
			"no loopKey assigned: using pattern key".warn;
			conCatCue = newKey;
		},{
			conCatCue = loopKey.asSymbol;
		});

		loopCues.put(conCatCue,true);

		^name
	}

	prConCatPat { | conCatCue, clicks |
		clicks.do({ |clk|
			if(clk.isKindOf(ClickLoop),{"cannot loop a loop! Or can I?".throw})
		});

		conCatPat = Pdef(conCatCue,
			Pwhile({ loopCues.at(conCatCue) },
				Psym(Pseq(clickArray))
			)
		);

		^conCatPat
	}

	play { this.conCatPat.play }

	stop { this.conCatPat.stop }

}