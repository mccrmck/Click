AbstractClick {

	classvar <loopCues, <cueBufs;
	var <bpm, <beats, <beatDiv, <repeats, <>amp, <>out;
	var <pattern;

	var <>clickFreq = 1000;

	// add clock stuff.here?! Something like:
	// clock = clock ? TempoClock.default;

	// the play / stop methods need to be revisited now that the classes are returning Pbinds/Pseqs
	// do the play/stop methods maybe return the EventStreamPlayer that gets created with each class? TBD.....

	*initClass {
		loopCues = IdentityDictionary();
		cueBufs  = IdentityDictionary();

		StartUp.add{

			ServerBoot.add({ |server|
				var pathToSounds = Click.filenameSymbol.asString.dirname +/+ "sounds/";

				PathName(pathToSounds).entries.do({ |entry|
					var cueName = entry.fileNameWithoutExtension.asSymbol;
					var buffer = Buffer.read(server,entry.fullPath);

					cueBufs.put(cueName,buffer);
				});
			},\default);

			SynthDef(\clickSynth,{
				var env = Env.perc(0.001, 0.25).kr(2);
				var sig = LFTri.ar(\freq.kr(1000));
				sig = sig * env * \amp.kr(0.25);
				OffsetOut.ar(\out.kr(0),sig);
			}).add;

			SynthDef(\clickCuePB,{
				var bufnum = \buf.kr();
				var sig = PlayBuf.ar(1,bufnum,BufRateScale.kr(bufnum),doneAction: 2);
				sig = sig * \amp.kr(0.25);
				OffsetOut.ar(\out.kr(0),sig);
			}).add;
		}
	}

	init {
		var barArray = this.makeBarArray;
		pattern      = this.makePattern(barArray);
	}

	makeBarArray {
		var barArray;

		if(beats.isInteger.not || beatDiv.isInteger.not,{
			beats = beats.floor;
			beatDiv = beatDiv.floor;
			"beats and beatDiv must be integers - they've been floored".warn;
		});

		if(beatDiv > 1,{
			var subDiv = Array.fill(beatDiv,{ 1 });
			subDiv[0] = 1.5;
			barArray = Array.fill(beats,subDiv);
			barArray = barArray.flat;
			barArray[0] = 2;
		},{
			if(beats == 1,{
				barArray = [2]
			},{
				barArray = Array.fill(beats,{ 1 });
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
			cueBar = clickBar.collect({ |item|
				if(item == 2,{ \note },{ \rest })
			})
		});
		^cueBar;
	}

	play { ^this.pattern.play }

	stop { ^this.pattern.stop }   // this doesn't work of course!!

	duration {
		var beatDur = 60 / this.bpm;
		^beatDur * beats * repeats;
	}
}

Click : AbstractClick {

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).init;
	}

	makePattern { |barArray|
		var dur = 60 / (bpm * beatDiv);

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, dur,
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),             // allows me to pass both/either floats and {bus.getSynchronous}...a hack? Or exploiting polymorphism?!
			\out, Pfunc({ out }),                   // do I need the Pfunc here? Is this also for some bus trickery, or?
		)
	}
}

ClickCue : AbstractClick {

	var <>cueKey;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, cueKey = 'bell', amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).cueKey_(cueKey.asSymbol).init;
	}

	makePattern { |barArray|
		var dur    = 60 / (bpm * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, dur,
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			),

			Pbind(
				\instrument, \clickCuePB,
				\dur, dur,
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		])
	}
}

ClickEnv : AbstractClick {

	var >bpmArray, >curve;
	var <tempoArray;

	*new { |bpmStartEnd = #[60,120], beats = 1, beatDiv = 1, repeats = 1, curve = 0, amp = 0.5, out = 0|
		if( bpmStartEnd.isArray.not or: { bpmStartEnd.size != 2 },{
			"bpmStartEnd must be an Array of 2 values".throw
		});
		^super.newCopyArgs(bpmStartEnd, beats, beatDiv, repeats, amp, out)
		.bpmArray_(bpmStartEnd).curve_(curve).init;
	}

	makePattern { |barArray|
		var strokes = beats * beatDiv;
		var bpms    = bpmArray * beatDiv;
		tempoArray  = Array.fill(strokes,{ |i| i.lincurve(0, strokes - 1, bpms[0], bpms[1], curve) });

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, Pseq( 60 / tempoArray, repeats ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, inf ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		)
	}

	duration {
		var durs = (60 / this.tempoArray).sum;
		^durs * repeats;
	}

	plot { (this.tempoArray / this.beatDiv).plot }
}

ClickEnvCue : AbstractClick {

	var <>cueKey;
	var >bpmArray, >curve;
	var <tempoArray;

	*new { |bpmStartEnd = #[60,120], beats = 1, beatDiv = 1, repeats = 1, curve = 0, cueKey = 'bell', amp = 0.5, out = 0|
		if( bpmStartEnd.isArray.not or: { bpmStartEnd.size != 2 },{
			"bpmStartEnd must be an Array of 2 values".throw
		});
		^super.newCopyArgs(bpmStartEnd, beats, beatDiv, repeats, amp, out)
		.cueKey_(cueKey.asSymbol).bpmArray_(bpmStartEnd).curve_(curve).init;
	}

	makePattern { |barArray|
		var strokes = beats * beatDiv;
		var bpms    = bpmArray * beatDiv;
		var cueBar  = this.makeCueBar(barArray);
		tempoArray  = Array.fill(strokes,{ |i| i.lincurve(0, strokes - 1, bpms[0], bpms[1], curve) });

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, Pseq( 60 / tempoArray, repeats ),
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, inf ),
				\amp, Pfunc({ amp.value }),
				\out, Pfunc({ out }),
			),
			Pbind(
				\instrument, \clickCuePB,
				\dur, Pseq( 60 / tempoArray, repeats ),
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		])
	}

	duration {
		var durs = (60 / this.tempoArray).sum;
		^durs * repeats
	}

	plot { (this.tempoArray / this.beatDiv).plot }

}

ClickLoop : AbstractClick {                                            // this has not been touched yet

	var <>loopCue;

	*new { |bpm = 60, beats = 1, beatDiv = 1, repeats = 1, loopKey = nil, amp = 0.5, out = 0|
		^super.newCopyArgs(bpm, beats, beatDiv, repeats, amp, out).loopCue_(loopKey).init;
	}

	keyCheck {
		if(loopCue.isNil,{
			var key = UniqueID.next;
			"no loopKey assigned - using unique key: %".format( key ).warn;
			loopCue = key.asSymbol;
		});

		loopCues.put(loopCue, true);
	}

	makePattern { |barArray|
		var dur = 60 / (bpm * beatDiv);
		this.keyCheck;

		if(repeats != inf,{
			^Pbind(
				\instrument, \clickSynth,
				\dur, dur,
				\freq,Pwhile({ loopCues.at(loopCue) }, Pfunc({ clickFreq }) * Pseq( barArray, repeats )),
				\amp, Pfunc({ amp.value }),
				\out, Pfunc({ out }),
			)
		},{
			MethodError("ClickLoop must have finite length").throw
		});
	}
}

// made to handle PolyTempoComposer output (ie. arrays of onsets like: [ 0, 0.791, 1.469, 2.069, 2.615, 3.2 ] )
ClickPTC : AbstractClick {

	var >onsetArray;
	var <deltas;

	*new { |onsetArray = #[0], beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		var beats = (onsetArray.size - 1) / beatDiv;
		^super.newCopyArgs('ptc', beats.asInteger, beatDiv, repeats, amp, out).onsetArray_(onsetArray).init
	}

	makePattern { |barArray|
		onsetArray = onsetArray.differentiate[1..];
		deltas = onsetArray;

		^Pbind(
			\instrument, \clickSynth,
			\dur, Pseq( onsetArray, inf ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		);
	}

	duration { ^deltas.sum }
}

ClickMan : AbstractClick {

	var >bpmArray;

	*new { |bpmArray = #[60], beatDiv = 1, repeats = 1, amp = 0.5, out = 0|
		^super.newCopyArgs(bpmArray[0], bpmArray.size, beatDiv, repeats, amp, out)
		.bpmArray_(bpmArray).init;
	}

	makePattern { |barArray|
		var dur = 60 / (bpmArray.dupEach(beatDiv) * beatDiv);

		^Pbind(
			\instrument, \clickSynth,
			\type,\grain,
			\dur, Pseq( dur.flat, inf ),
			\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
			\amp, Pfunc({ amp.value }),
			\out, Pfunc({ out }),
		);
	}

	duration {
		var barDur = (60 / bpmArray).sum;
		^barDur * repeats;
	}
}

ClickManCue : AbstractClick {

	var <>cueKey;
	var >bpmArray;

	*new { |bpmArray = #[60], beatDiv = 1, repeats = 1, cueKey = 'bell', amp = 0.5, out = 0|
		^super.newCopyArgs(bpmArray[0], bpmArray.size, beatDiv, repeats, amp, out)
		.bpmArray_(bpmArray).cueKey_(cueKey.asSymbol).init;
	}

	makePattern { |barArray|
		var dur    = 60 / (bpmArray.stutter(beatDiv) * beatDiv);
		var cueBar = this.makeCueBar(barArray);

		^Ppar([
			Pbind(
				\instrument, \clickSynth,
				\type,\grain,
				\dur, Pseq( dur.flat, inf ),
				\freq,Pfunc({ clickFreq }) * Pseq( barArray, repeats ),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			),

			Pbind(
				\instrument, \clickCuePB,
				\dur, Pseq( dur.flat, inf ),
				\type,Pseq( cueBar, repeats ),
				\buf, Pfunc({ cueBufs[cueKey] }),
				\amp, Pfunc({ amp.value }) * -3.dbamp,
				\out, Pfunc({ out }),
			)
		]);
	}

	duration {
		var barDur = (60 / bpmArray).sum;
		^barDur * repeats;
	}
}

ClickRest : AbstractClick {

	*new { |bpm = 60, beats = 1, repeats = 1|
		^super.newCopyArgs(bpm, beats, 1, repeats, 0, 0).init;     // is there a better way to handle amp, out args?
	}

	init {
		pattern = this.makePattern;
	}

	makePattern {
		var dur = (60 / bpm) * beats;
		^Pbind( \dur, Pseq([ Rest(dur) ], repeats ) );
	}
}

/* classes for composing Clicks */

ClickConCat : AbstractClick {

	var <clickArray;

	*new { |reps ...clicks|
		^super.new.amp_(0.5).out_(0).init(reps, clicks);
	}

	init { |reps, clicks|
		repeats    = reps;
		clickArray = clicks.asArray.flat;
		pattern    = this.makePattern( reps );
	}

	makePattern { |repeats|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });
		^Pseq(sumArray,repeats);
	}

	bpm     { ^clickArray.first.bpm }
	beats   { ^clickArray.collect({ |clk| clk.beats }).dup( repeats ) }
	beatDiv { ^clickArray.collect({ |clk| clk.beatDiv }).dup( repeats ) }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk| clk.amp = amp });
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk| clk.out = out });
	}

	duration {
		var durs = clickArray.collect({ |clk| clk.duration });
		^durs.sum * repeats;
	}
}

ClickConCatLoop : AbstractClick {

	var <clickArray, <>loopCue;

	*new { |loopKey ...clicks|
		^super.new.loopCue_(loopKey).amp_(0.5).out_(0).init(clicks);
	}

	init { |clicks|
		clickArray = clicks.asArray.flat;
		pattern    = this.makePattern;
	}

	keyCheck {
		if(loopCue.isNil,{
			var key = UniqueID.next;
			"no loopKey assigned - using unique key: %".format( key ).warn;
			loopCue = key.asSymbol;
		});

		loopCues.put(loopCue, true);
	}

	makePattern { |key, loopKey|
		var sumArray = clickArray.deepCollect(3,{ |clk| clk.pattern });
		this.keyCheck;
		^Pwhile({ loopCues.at(loopCue) }, Pseq(sumArray) );
	}

	bpm     { ^clickArray.first.bpm }
	beats   { ^clickArray.collect({ |clk| clk.beats }).dup( repeats ) }
	beatDiv { ^clickArray.collect({ |clk| clk.beatDiv }).dup( repeats ) }

	repeats { "This is a looping class, calling this method is probably a mistake".error }

	amp_ { |val|
		amp = val;
		clickArray.do({ |clk| clk.amp = amp });
	}

	out_ { |val|
		out = val;
		clickArray.do({ |clk| clk.out = out });
	}

	duration {
		var durs = clickArray.collect({ |clk| clk.duration });
		^durs.sum
	}
}