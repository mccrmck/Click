AbstractClick {

	classvar <all, <loopCues;
	var <bpm, <beats, <beatDiv, <repeats, <>amp, <>out;
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

		^"%_%%%o%".format(bpm,beats,subDiv,repeats,out).asSymbol;
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

	play { this.pattern.play }

	stop { this.pattern.stop }

	clear {
		this.pattern.clear;
		all.removeAt(key)
	}

	*clear { this.all.do.clear }  // check this??? Other examples use .copy.do....why?

}

Click : AbstractClick {

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

		^Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq([ dur ],inf),
				\freq, Pseq(1000 * barArray,repeats),     // a bit hacky maybe? allows me to pass both/either floats and {bus.getSynchronous}...
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		);
	}
}

ClickCue : AbstractClick {

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
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "sounds" +/+ "cueBell.wav";                   // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = ("q" ++ prefix).asSymbol;

		^Pdef(key,
			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq([ dur ],inf),
					\freq,Pseq(1000 * barArray,repeats),
					\amp, Pfunc({ amp.value }) * -3.dbamp,
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq([ dur ],inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
					\amp, Pfunc({ amp.value }) * -3.dbamp,
					\outBus, Pfunc({ out }),
				)
			])
		)
	}
}

ClickEnv : AbstractClick {  // this has to be rethought a bit...I don't think the beats argument has any influence over the final outcome...

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

		^Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseg(tempi, dur, curve, repeats),
				\freq,Pseq(1000 * barArray,inf),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		)
	}
}

ClickLoop : AbstractClick {

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
			^Pdef(key,
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

ClickMan : AbstractClick {

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("man", bpmArray.size, beatDiv, repeats, amp, out).init(bpmArray);
	}

	init { |bpmArray|
		var prefix = this.makePrefix;
		var barArray = this.makeBarArray;
		pattern = this.makePattern(prefix, barArray, bpmArray);

		all.put(key,pattern);
	}

	makePattern { |prefix, barArray, bpmArray|
		var dur = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
		key = prefix.asSymbol;                                                                           // make more unique keys!!!

		^Pdef(key,
			Pbind(
				\instrument, \clickSynth,
				\dur, Pseq(dur.flat,inf),
				\freq, Pseq(1000 * barArray,repeats),
				\amp, Pfunc({ amp.value }),
				\outBus, Pfunc({ out }),
			)
		);
	}
}

ClickManCue : AbstractClick {

	classvar bufnum;

	*new { |bpmArray = ([60]), beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs("manQ", bpmArray.size, beatDiv, repeats, amp, out).init(bpmArray);
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
		var path = Platform.userExtensionDir +/+ "Tools/Click" +/+ "sounds" +/+ "cueBell.wav";            // this seems a bit messy, no?
		bufnum = Buffer.read(Server.default,path);

		key = prefix.asSymbol;                                                                           // make more unique keys!!!

		^Pdef(key,
			Ppar([
				Pbind(
					\instrument, \clickSynth,
					\dur, Pseq(dur.flat,inf),
					\freq, Pseq(1000 * barArray,repeats),
					\amp, Pfunc({ amp.value }) * -3.dbamp,
					\outBus, Pfunc({ out }),
				),

				Pbind(
					\instrument, \clickCuePlayback,
					\dur, Pseq(dur.flat,inf),
					\type, Pseq(cueBar,repeats),
					\bufnum, Pfunc({ bufnum }),
					\amp, Pfunc({ amp.value }) * -3.dbamp,
					\outBus, Pfunc({ out }),
				)
			])
		);
	}
}

ClickRest : AbstractClick {

	*new {}

	init {}

	makePattern {}
}

/* === not sure what to call these....pseudoClicks? MetaClicks? === */

ClickConCat : AbstractClick {

	var <clickArray;

	*new { |reps ...clicks|
		^super.new.amp_(0.5).out_(0).init(reps, clicks);
	}

	init { |reps, clicks|
		repeats = reps;
		clickArray = clicks.asArray.flat;     // this flattens things...but could perhaps handle parallel patterns??? Could be nice..
		this.makeConCatKey;
		pattern = this.makePattern(key,repeats);

		all.put(key,pattern);
	}

	makeConCatKey {
		var newKey = "cc%".format(repeats.asString);
		var clickKeys = clickArray.deepCollect(3,{ |clk| clk.key });
		clickKeys.do({ |clkKey| newKey = newKey ++ clkKey.asString});
		key = newKey.removeEvery("_").asSymbol;

		^key
	}

	makePattern { |key, repeats|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });

		^Pdef(key,
			Pseq(sumArray,repeats)
		);
	}

	bpm { ^clickArray.first.bpm }

	beats { ^this }
	beatDiv { ^this }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk|                       // change this to deepDo if trying to accommodate parallel patterns?
			clk.amp = amp;
		});

		^this
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk|                       // change this to deepDo if trying to accommodate parallel patterns?
			clk.out = out;
		});

		^this
	}
}

ClickConCatLoop : AbstractClick {

	var <clickArray, <loopCue;

	*new { |loopKey ...clicks|
		^super.new.amp_(0.5).out_(0).init(loopKey, clicks);
	}

	init { |loopKey, clicks|
		clickArray = clicks.asArray.flat;                // this flattens things...but could perhaps handle parallel patterns??? Could be nice..
		this.makeConCatKey;
		pattern = this.makePattern(key,loopKey);

		all.put(key,pattern);
		loopCues.put(loopCue,true);
	}

	makeConCatKey {
		var newKey = "cl";                                                 // make more unique keys!!!
		var clickKeys = clickArray.deepCollect(3,{ |clk| clk.key });
		clickKeys.do({ |clkKey| newKey = newKey ++ clkKey.asString});
		key = newKey.removeEvery("_").asSymbol;

		^key
	}

	makePattern { |key, loopKey|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });

		if(loopKey.isNil,{
			"no loopKey assigned: using pattern key".warn;
			loopCue = key;
		},{
			loopCue = loopKey.asSymbol;
		});

		^Pdef(key,
			Pwhile({ loopCues.at(loopCue) }, Pseq(sumArray) )
		);
	}

	bpm { ^clickArray.first.bpm }

	beats { ^this }
	beatDiv { ^this }
	repeats { ^this }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk|                       // change this to deepDo if trying to accommodate parallel patterns?
			clk.amp = amp;
		});

		^this
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk|                       // change this to deepDo if trying to accommodate parallel patterns?
			clk.out = out;
		});

		^this
	}
}