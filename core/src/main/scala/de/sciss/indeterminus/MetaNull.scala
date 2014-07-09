/*
 *  MetaNull.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.indeterminus

import java.io.FileFilter
import de.sciss.synth.io.{AudioFileSpec, AudioFile}
import de.sciss.span.Span
import collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import de.sciss.mellite
import de.sciss.strugatzki.{FeatureCorrelation, FeatureExtraction}
import de.sciss.processor.Processor
import de.sciss.synth.proc
import de.sciss.mellite._
import de.sciss.synth.proc.{Timeline, Obj, ExprImplicits, FadeSpec, ProcKeys, ArtifactLocation, Grapheme}
import de.sciss.file._
import de.sciss.lucre.synth.InMemory

object MetaNull {
  def perform(in: File, out: File, config: Nullstellen.Config): Unit = {
    val inSpec  = AudioFile.readSpec(in)
    val spans   = findNonSilentSpans(in) // .take(2)

    import de.sciss.mellite.executionContext

    val matches = spans.flatMap { span =>
      val cb    = Nullstellen.Config()
      cb.read(config)
      cb.layer        = in
      cb.tlSpan       = span // Span(0L, spec.numFrames)
      cb.layerOffset  = span.start
      val p = Nullstellen(cb)
      var lastProg = 0
      p.addListener {
        case prog @ Processor.Progress(_, _) =>
          val newProg = prog.toInt / 2
          while (lastProg < newProg) {
            print('#')
            lastProg += 1
          }
      }
      p.start()
      val res = Await.result(p, Duration.Inf)
      println()

      //      if (res.nonEmpty) {
      //        val dropEqual = res.init.dropWhile(_.head._2.sim > 0.9) :+ res.last
      //        Some(dropEqual.head)
      //
      //      } else None
      res
    }

    println(s":::: ${if (matches.size <= 5) "All" else "First 5"} Matches ::::")
    matches.take(5).foreach(println)

    bounce(inSpec, out, matches)

    // ...
  }

  private def bounce(inSpec: AudioFileSpec, out: File, matches: Vec[Vec[(Long, FeatureCorrelation.Match)]]): Unit = {
    type I            = InMemory
    implicit val sys  = InMemory()
    val p = sys.step { implicit tx =>
      val group = proc.Timeline[I] // ProcGroup.Modifiable[I]
      matches.foreach { case (time, m) +: _ =>    // just mono right now
        val loc       = ArtifactLocation.Modifiable[I](m.file.parent.parent) // XXX TODO: bug in ArtifactLocation -- needs one sub-level at least
        val artifact  = loc.add(m.file)
        val spec      = AudioFile.readSpec(m.file)
        val offset    = 0L
        val gain      = math.sqrt(m.boostIn * m.boostOut)
        val imp = ExprImplicits[I]
        import imp._
        val grapheme  = Grapheme.Expr.Audio(artifact, spec, offset, gain)
        val span      = m.punch
        val srRatio   = spec.sampleRate / Timeline.SampleRate
        val gOffset   = (span.start / srRatio).toLong
        val tlSpan    = Span(time, time + (span.length / srRatio).toLong)
        val (_, proc) = ProcActions.insertAudioRegion(group = group, time = tlSpan, track = 0, grapheme = grapheme,
                                                      gOffset = gOffset, bus = None)
        val fdInLen   = (0.02 * Timeline.SampleRate).toLong
        val fdIn      = FadeSpec.Expr.newConst[I](FadeSpec(fdInLen))
        val fdOut     = FadeSpec.Expr.newConst[I](FadeSpec(math.min(tlSpan.length - fdInLen, (0.5 * Timeline.SampleRate).toLong)))
        proc.attr.put(ProcKeys.attrFadeIn , Obj(FadeSpec.Elem(fdIn )))
        proc.attr.put(ProcKeys.attrFadeOut, Obj(FadeSpec.Elem(fdOut)))
      }

      val bnc   = proc.Bounce[I, I]
      val bc    = bnc.Config()
      // implicit val ser = proc.ProcGroup.Modifiable.serializer[I]
      bc.group  = ??? // tx.newHandle(group)
      // bc.init   = ...
      val sc                = bc.server
      sc.outputBusChannels  = 1
      sc.sampleRate         = inSpec.sampleRate.toInt
      sc.nrtOutputPath      = out.path
      bc.span               = Span(0L, inSpec.numFrames)

      bnc(bc)
    }

    var lastProg = 0
    p.addListener {
      case prog @ Processor.Progress(_, _) =>
        val newProg = prog.toInt / 2
        while (lastProg < newProg) {
          print('#')
          lastProg += 1
        }
    }
    println("Bouncing...")
    p.start()
    Await.result(p, Duration.Inf)
  }

  private def findNonSilentSpans(in: File): Vec[Span] = {
    val afIn  = AudioFile.openRead(in)
    try {
      val bufSz     = 8192
      val buf       = afIn.buffer(bufSz)
      val minSil    = afIn.sampleRate.toInt
      val thresh    = 1.0e-5f
      var read      = 0L
      val numFrames = afIn.numFrames
      val numCh     = afIn.numChannels
      var firstLoud = 0L
      var lastLoud  = 0L
      var isSilent  = true
      var _spans    = Vector.empty[Span]
      while (read < numFrames) {
        val chunk = math.min(bufSz, numFrames - read).toInt
        afIn.read(buf, 0, chunk)
        for (i <- 0 until chunk) {
          val isLoud = (0 until numCh).exists(ch => math.abs(buf(ch)(i)) > thresh)
          if (isLoud) {
            lastLoud = read + i
            if (isSilent) {
              firstLoud = lastLoud
              isSilent  = false
            }
          } else {
            if (!isSilent) {
              if (read + i - lastLoud >= minSil) {
                isSilent = true
                _spans :+= Span(firstLoud, lastLoud)
              }
            }
          }
        }

        read += chunk
      }

      if (firstLoud < lastLoud && !isSilent) _spans :+= Span(firstLoud, lastLoud) // 'flush'

      println(":::: Non-silent Spans ::::")
      _spans.foreach(println)

      _spans

    } finally {
      afIn.close()
    }
  }

  def makeDatabaseAliases(inDir: File, outDir: File): Unit = {
    def loop(base: File): Vector[File] = {
      val sub   = base.listFiles(new FileFilter {
        def accept(f: File): Boolean = f.isDirectory
      })
      val files = acceptableAudioFiles(base)
      if (sub != null) sub.flatMap(loop).toVector ++ files else files
    }

    import sys.process._

    val inFiles = loop(inDir)
    inFiles.foreach { inF =>
      val outF = outDir / inF.name
      if (!outF.exists()) {
        Seq("ln", "-s", inF.path, outDir.path).!
      }
    }
  }

  private def acceptableAudioFiles(in: File): Vector[File] = {
    val files0 = in.listFiles(new FileFilter {
      def accept(f: File): Boolean = f.isFile && f.canRead && (AudioFile.identify(f) match {
        case Some(_) =>
          val spec = AudioFile.readSpec(f)
          spec.numFrames > 88200 && spec.numChannels >= 1 && spec.numChannels <= 4
        case _ => false
      })
    })
    if (files0 == null) Vector.empty else files0.toVector
  }

  def runExtractors(inDir: File, outDir: File): Unit = {
    import mellite.executionContext
    val files   = acceptableAudioFiles(inDir)
    files.foreach { inF =>
      val name    = plainName(inF)
      val featOut = featureFile(name, outDir)
      if (!featOut.exists()) {
        val metaOut = extrMetaFile(name, outDir)
        val config            = FeatureExtraction.Config()
        config.audioInput     = inF
        config.featureOutput  = featOut
        config.metaOutput     = Some(metaOut)
        println(name)
        val p = FeatureExtraction(config)
        p.start()
        Await.result(p, Duration.Inf)
      }
    }
  }

  private def featureFile (plain: String, dir: File): File = new File(dir, s"${plain}_feat.aif")
  private def extrMetaFile(plain: String, dir: File): File = new File(dir, s"${plain}_feat.xml")

  private def plainName(f: File): String = {
    val n   = f.getName
    val i   = n.lastIndexOf('.')
    val n1  = if (i >= 0) n.substring(0, i) else n
    if (n1.endsWith("_feat")) n1.dropRight(5) else n1
  }
}