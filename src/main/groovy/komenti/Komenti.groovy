package komenti

import klib.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import groovyx.gpars.*
import groovy.json.*
import groovyx.gpars.GParsPool

public class Komenti {
  static def gen_roster(o) {
    def cLoader = getClass()
    def templateFile = cLoader.getResourceAsStream('/templates/roster.json')
    if(o['with-abstracts-download']) {
      templateFile = cLoader.getResourceAsStream('/templates/roster_with_abstract_download.json')
    }
    if(o['mine-relationship']) {
      templateFile = cLoader.getResourceAsStream('/templates/roster_mine_relationship.json')
    }
    if(o['suggest-axiom']) {
      templateFile = cLoader.getResourceAsStream('/templates/roster_suggest_axiom.json')
    }

    def roster = new JsonSlurper().parseText(templateFile.getText())
    
    // TODO this can probably mostly be automated
    if(o.q) {
      roster.commands.find { it.id == 'class_query' }.args.query = o.q
    } else {
      roster.commands.find { it.id == 'class_query' }.args['class-list'] = o.c
    }
    if(o.o) { roster.commands.find { it.id == 'class_query' }.args.ontology = o.o }

    if(o['with-abstracts-download']) {
      if(o.l) { roster.commands.find { it.command == 'get_abstracts' }.args.limit = o.l }
    }

    if(!o['with-abstracts-download'] && !o['with-metadata-download']) {
     roster.commands = roster.commands.findAll { it.command != 'get_abstracts' && it.command != 'get_metadata' }
     roster.commands.find { it.command == 'annotate' }.text = o.t 
    } else if(!o['with-abstracts-download']) {
     roster.commands = roster.commands.findAll { it.command != 'get_abstracts' }
    } else if(!o['with-metadata-download']) {
     roster.commands = roster.commands.findAll { it.command != 'get_metadata' }
    }

    if(o['mine-relationship']) {
      roster.commands.find { it.command == 'summarise_entity_pair' }.args['class-list'] = o.c
    }

    if(o['suggest-axiom']) {
      def cq = roster.commands.find { it.id == 'class_query' }
      cq.args['class-list'] = o.c
      cq.args['ontology'] = o.o

      def eq = roster.commands.find { it.id == 'entity_query' }
      eq.args['class-list'] = o.entity
      eq.args['ontology'] = o.eo ? o.eo : o.o

      def rq = roster.commands.find { it.id == 'relation_query' }
      rq.args['ontology'] = o.oo ? o.oo : o.o

      def qq = roster.commands.find { it.id == 'quality_query' }
      qq.args['class-list'] = o.quality
      qq.args['ontology'] = o.qo ? o.qo : o.o

      def sa = roster.commands.find { it.command == 'suggest_axiom' }
      sa.args['default-relation'] = o['default-relation']
      sa.args['default-entity'] = o['default-entity']
    }

    new File(o.out).text = JsonOutput.prettyPrint(JsonOutput.toJson(roster))
    println "Roster file saved to $o.out, where you can edit it manually. You can run it with 'groovy Komenti auto -r $o.out'"
  }

  static def auto(o) {
    def roster = new JsonSlurper().parse(new File(o.r))

    roster.commands.each { item ->
      def newArgs = [item.command] + item.args.collect { k, v -> 
        if(k.size() == 1) { k = "-$k" } else { k = "--$k" } 
        if(v instanceof String && v.split(' ').size() > 1 && v[0] != '"') { v = '"' + v + '"' }
        if(v instanceof Integer || v instanceof Boolean) { v = "$v" } // foolish cliBuilder ...

        [k, v] 
      }.flatten()
      newArgs.removeAll("true")

      App.main(newArgs)
    }
  }

  static def query(o) {
    def vocabulary = new Vocabulary(o.out)

    if(o['object-properties']) {
      KomentLib.AOGetObjectProperties(o.o, { oProps ->
        KomentLib.buildEntityNames(vocabulary, 'object-properties', o, oProps)
      })
    } else { // regular class query
      def queries = [o.q]
      if(o.c) {
        def f = new File(o.c)
        if(f.exists()) {
          queries = f.text.split('\n')
        } else {
          queries = o.c.split(',')
        }
      }
      
      queries.each { q ->
        def ont = o.o
        if(q.indexOf('\t') != -1) { (q, ont) = q.split('\t') }
        KomentLib.AOSemanticQuery(q, ont, o['query-type'], { classes ->
          KomentLib.buildEntityNames(vocabulary, q, o, classes)
        })
      }
    }

    vocabulary.write(o['append'])
    if(o.out) {
      println "Saved ${vocabulary.labelSize()} labels for ${vocabulary.size()} terms to ${o.out}"
    }
  }

  static def get_metadata(o) {
    def outDir = getOutDir(o)
    def files = [:]
    def komentisto = new Komentisto(o.l, true, o['family-modifier'], o['allergy-modifier'], false,  o['exclude'], o['threads'] ?: 1)

    def excludeGroups = []
    def entityLabels = []
    def classLabels = [:]
    if(o['exclude-groups']) {
      excludeGroups = o['exclude-groups'].split(',')
    }
    new File(o.l).splitEachLine('\t') { // TODO: integrate this into loadClassLabels
      if(it[2] == 'entity') { entityLabels << it[0].replaceAll('\\\\', '') }
      if(!excludeGroups.contains(it[2])) {
        if(!classLabels.containsKey(it[1])) { classLabels[it[1]] = [ l: [], o: it[3] ] }
          classLabels[it[1]].l << it[0]
        }
      }

    if(!o['decompose-entities']) { entityLabels = [] }

    classLabels.each { iri, l ->
      KomentLib.AOSemanticQuery("<$iri>", l.o, "equivalent", { classes ->
        // we want the actual class, not just semantically equivalent ones. although tbh it might be better to get the metadata from those too. it has to be semantically equivalent to this class, after all
        def c = classes.find { it.class == iri }
        def metadata = KomentLib.AOExtractMetadata(c, entityLabels)
        if(o['lemmatise']) { // we do it per line here, since it's a field based document
          metadata = metadata.split('\n').collect { komentisto.lemmatise(it) }.join('\n')
        }
        files[l.l[0]] = metadata
      })
    }

    println "Writing metadata files for ${files.size()} classes."
    files.each { n, c ->
      new File(outDir.getAbsolutePath() + '/' + n.replaceAll('/','') + '.txt').text = c
    } 

    println "Done"
  }

  static def annotate(o) {
    def vocab = Vocabulary.loadFile(o.l) 
    def files = getFileList(o)

    // getting a bit spaghet-ey
    if(!o['extract-triples']) {
      def outWriter = new BufferedWriter(new FileWriter(o.out))
    }

    // So the idea is that we'll only evaluate triples
    def aList = new AnnotationList(o.out, !o['extract-triples'])
    def tList
    def tripleAnnotator
    if(o['extract-triples']) {
      tList = new TermTripleList(o.out, true) 
    } 

    if(o['verbose']) { println "Initialising annotators" }
    def komentisto = new Komentisto(vocab, 
      o['disable-modifiers'], 
      o['family-modifier'], 
      o['allergy-modifier'],
      o['extract-triples'],
      o['exclude'], 
      o['threads'] ?: 1)
      
    println "Annotating ${files.size()} files ..."
    def i = 0
    GParsPool.withPool(o['threads'] ?: 1) { p -> 
    files.eachParallel{ f ->
      def (name, text) = [f.getName(), f.text]
      if(name =~ /(?i)pdf$/) { 
        text = new PDFReader(f).getText() 
        if(o['write-pdfs-to-dir']) {
          def dir = new File(o['write-pdfs-to-dir'])
          if(!dir.exists()) { dir.mkdir() }
          new File(dir, f.getName() + '.txt').text = text
        }
      }

      def annotations
      if(o['per-line']) {
        text.tokenize('\n').eachWithIndex { lineText, z ->
          annotations = komentisto.annotate(name, lineText, z+i)
        }
      } else {
        annotations = komentisto.annotate(name, text)
      }

      if(o['extract-triples']) {
        annotations.groupBy { it.sentenceId }.each { sid, sAnns ->
          def termAnns = sAnns.findAll { it.group == 'terms' }
          def rAnns = sAnns.find { it.group == 'object-properties' }

          // check the rAnns are between the termAnns??
          if(termAnns.size() >= 2 && (rAnns || o['allow-unmatched-relations'])) {
            def newTriples = komentisto.extractTriples("${name}_${sAnns[0].sentenceId}", sAnns[0].text, o['allow-unmatched-relations'])
            tList.add(newTriples.max { it.object.label.size() })
          }
        }
      } else {
        aList.add(annotations)
      }

      if(o.verbose) {
        println "${i}/${files.size()}"
      }
    }
    }

    if(!o['extract-triples']) {
      aList.finishWrite() 
    } else {
      tList.finishWrite()
    }

    println "Annotation complete"
  }

  // TODO needs to use Annotation class
  static def add_modifier(o) {
    def komentisto = new Komentisto(o.l, false)

    def newAnnotations = []
    def i = 0
    def aSize = new File(o.a).text.split('\n').size() // ugly

    def annoFile = []
    new File(o.a).splitEachLine('\t') { annoFile << it }

    def cache = []
    new File(o.out).splitEachLine('\t') { cache << it[0] }

    def outWriter = new BufferedWriter(new FileWriter(o.out, true));

    GParsPool.withPool(o['threads'] ?: 1) { p -> 
    annoFile.eachParallel {
      if(!cache.contains(it[0]) && it[1] && it[5]) {
        def res = komentisto.evaluateSentenceConcept(it[5], it[1])
        def tags = res.findAll { it.getValue() }.collect { it.getValue() }.join(',')

        outWriter.write(it.join('\t') + '\n')
        if(o.verbose) { println "Adding modifiers (${++i}/$aSize)" }
      } else {
        i++
      }
    }
    }

    outWriter.flush()
    outWriter.close()
  }

  static def get_abstracts(o) {
    def outDir = getOutDir(o)
    def classLabels = [:] // TODO integrate this gIndex etc with the loadClassLabels function
    def gIndex = 1
    def excludeGroups = []
    if(o['exclude-groups']) {
      excludeGroups = o['exclude-groups'].split(',')
    }
    if(o['group-by-query']) {
      gIndex = 2
    }
    new File(o.l).splitEachLine('\t') { // TODO: integrate this into loadClassLabels
      if(!excludeGroups.contains(it[2])) {
        if(!classLabels.containsKey(it[gIndex])) { classLabels[it[gIndex]] = [] }
        classLabels[it[gIndex]] << it[0]
      }
    }

    println "Finding articles for ${classLabels.size()} classes ..."

    def aids = []
    if(o.conjunction) {
      def query = '(' + classLabels.collect { c, l -> '"' + l.join('" OR "') + '"' }.join(') AND (') + ')'
      KomentLib.PMCSearch(query, o['count-only'], { result -> aids << result })
    } else { // disjunction (default)
      def newLabels = []
      def thisLabelGroup = []
      classLabels.each { cls, labels ->
        thisLabelGroup << labels
        if(thisLabelGroup.size() == 10) {
          newLabels << thisLabelGroup.flatten()
          thisLabelGroup = []
        }
      }
      newLabels << thisLabelGroup.flatten()

      def i = 0
      newLabels.each { labels ->
        KomentLib.PMCSearchTerms(labels, o['count-only'], { result -> aids << result })
        println "${++i}/${newLabels.size()}"
      }
    }

    aids = aids.flatten()

    if(o['count-only']) {
      println "Found ${aids.sum()} articles ..."
    } else {
      println "Found ${aids.size()} articles ..."

      if(o.limit) { 
        aids = aids.subList(0, o.limit)
      }

      println "Downloading abstracts for ${aids.size()} articles ..."

      if(o['id-list-only']) {
        writeOutput(aids.join('\n'), o, "Saved pmcids to $o.out!")
      } else {
        def komentisto = new Komentisto(o.l, true, o['family-modifier'], o['exclude'], o['threads'] ?: 1)
        def abstracts = []
        aids.each { pmcid ->
          KomentLib.PMCGetAbstracts(pmcid, { a -> 
            if(a) { 
              if(o['lemmatise']) {
                a = komentisto.lemmatise(a)
              }
              abstracts << [ id: pmcid, text: a ]
            }
          })
        }

        println "Saving ${abstracts.size()} abstracts to ${outDir.getPath()}"

        abstracts.each { a ->
          new File(outDir.getAbsolutePath() + '/' + a.id + '.txt').text = a.text
        } 
      }
    }
  }

  static def summarise_entity_pair(o) {
    def classes = o.c.split(',')
    def g1 = classes[0]
    def g2 = classes[1]

    def groupLabels = [:]
    def classLabels = [:]
    def classGroups = [:]
    new File(o.l).splitEachLine('\t') {
      if(it[2] == g1 || it[2] == g2) {
        if(!classLabels.containsKey(it[1])) { classLabels[it[1]] = [] }
        classLabels[it[1]] << it[0]

        if(!groupLabels.containsKey(it[2])) { groupLabels[it[2]] = [] }
        groupLabels[it[2]] << it[1]

        classGroups[it[1]] = it[2]
      } 
    }

    def annotations = AnnotationList.loadFile(o.a)
    def fids = annotations.collect { it.f }.unique(false)

    def g1A = annotations.findAll { classGroup[it.termIri] == g1 }
    def g2A = annotations.findAll { classGroup[it.termIri] == g2 }

    println "$g1 is mentioned ${g1A.size()} times"
    println "$g2 is mentioned ${g2A.size()} times"

    // Group mentions by files

    def g1F = g1A.findAll { a -> g2A.find { it.f == a.f } }
    def bothMentionFiles = []

    g1F.each { a1 ->
      g2A.findAll { it.f == a1.f }.each { a2 ->
        bothMentionFiles << [ a1, a2 ]
      }
    }

    def bothCounts = [:]
    bothMentionFiles.each { l ->
      def key = l[0].label + ' and ' + l[1].label
      if(!bothCounts.containsKey(key)) {
        bothCounts[key] = [ count: 0, uncertain: 0, negated: 0, articles: [] ]
      }
      bothCounts[key].count++
      if(l[0].tags.contains('negated') || l[1].tags.contains('negated')) {
        bothCounts[key].negated++
      }
      if(l[0].tags.contains('uncertain') || l[1].tags.contains('uncertain')) {
        bothCounts[key].uncertain++
      }
      if(!bothCounts[key].articles.contains(l[0].f)) { bothCounts[key].articles << l[0].f }
    }

    println "Considered ${fids.size()} documents and ${annotations.size()} annotations"
    println "$g1 and $g2 are mentioned together ${bothMentionFiles.size()} times."
    bothCounts.each { key, c ->
      println "  $key ($c.count mentions, ${c.articles.size()} articles, $c.uncertain uncertain, $c.negated negated)"
    }
  }

  static def suggest_axiom(o) {
    // TODO probably just put this into an Labels class, replacing the loadLabels
      def groupClasses = [:]
      def classes = [:]
      def entity
      def relation
      new File(o.l).splitEachLine('\t') {
        groupClasses[it[1]] = it[2]
        if(it[2] == 'class') {
          if(!classes.containsKey(it[1])) {
            classes[it[1]] = it[0]
          }
        }
        if(it[2] == 'entity' && it[0] == o['default-entity']) {
          entity = [
            iri: it[1],
            label: it[0].replaceAll('\\\\',''),
            count: 0
          ]
        }
        if(it[2] == 'relation' && it[0] == o['default-relation']) {
          relation = [
            iri: it[1],
            label: it[0].replaceAll('\\\\',''),
            count: 0
          ]
        }
      }

      if(!relation) { println "Could not find default relation in label file." ; System.exit(1) }
      if(!entity) { println "Could not find default entity in label file." ; System.exit(1) }

      def sentences = [:]
      AnnotationList.loadFile(o.a).each {
        def sid = it.f ':' + it.sid
        if(!sentences.containsKey(sid)) { sentences[sid] = [] }
        sentences[sid] << ann
      }
      
      if(o['class-list']) { def cl = o['class-list'].split(',') ; classes = classes.findAll { cIRI, cLabel -> cl.contains(cLabel) } }

      // what the hell have i wrought
      classes.each { cIRI, cLabel ->
        def counts = [:]
        def qualityCounts = [:]
        def relationCounts = [:]
        def entityCounts = [:]

        // files that mention our class ...
        def classFiles = sentences.findAll { i, s -> s.any { it.f.indexOf(cLabel) != -1 } }.collect { i, s -> s.f }
        sentences.findAll { i, s -> classFiles.contains(s.f) }.each { id, annotations ->
          annotations.findAll { groupClasses[it.termIri] == 'entity' }.each { tentity ->
            if(!entityCounts.containsKey(tentity.iri)) { entityCounts[tentity.iri] = [ iri: tentity.iri, label: tentity.label, sids: [], count: 0] }
            entityCounts[tentity.iri].count++
            entityCounts[tentity.iri].sids << id
          }
          annotations.findAll { groupClasses[it.termIri] == 'quality' && it.iri != cIRI && it.label.indexOf(entity.label) == -1 }.each { quality ->
            if(quality) {
              if(!qualityCounts.containsKey(quality.iri)) { qualityCounts[quality.iri] = [ iri: quality.iri, label: quality.label, sids: [], count: 0] }
              qualityCounts[quality.iri].count++
              qualityCounts[quality.iri].sids << id
            }
          }
          annotations.findAll { groupClasses[it.termIri] == 'relation' }.each { trelation ->
            if(trelation) {
              if(!relationCounts.containsKey(relation.iri)) { relationCounts[relation.iri] = [ iri: relation.iri, label: relation.label, sids: [], count: 0] }
              relationCounts[relation.iri].count++
              relationCounts[relation.iri].sids << id
            }
          }
        }

        if(qualityCounts.size() > 0) {
          def bestQuality = qualityCounts.collect { it.getValue() }.inject { s, c -> c.count > s.count ? c : s }
          def bestEntity = entityCounts.collect { it.getValue() }.inject(entity) { s, c -> c.count > s.count ? c : s }
          def bestRelation = relationCounts.collect { it.getValue() }.inject(relation) { s, c -> c.count > s.count ? c : s }

          println "Suggested axiom for $cLabel ($cIRI): "
            println "  $bestEntity.label AND ($bestRelation.label SOME $bestQuality.label)"
            println "  <$bestEntity.iri> AND (<$bestRelation.iri> SOME <$bestQuality.iri>)"
          println 'Evidence:'
          println '  ' + bestQuality.sids.join(',  ')
        } else {
          println "No qualities found for $cLabel ($cIRI)"
        }
        println ''
        //def bestRelation = relationCounts.inject { s, iri, c -> c.count > s.count ? c : s }
      }

  }

  static def diagnose(o) {
    if(!o.a) { println 'Must provide a --annotations file to diagnose' ; System.exit(1) }

    def annotations = AnnotationList.loadFile(o.a)
    def documents = [:]
    def concepts = [:]

    def conceptFactor = 'termIri'
    if(o['by-group']) {
      conceptFactor = 'group'
    }

    annotations.each { s ->
      if(!concepts.containsKey(s.termIri)) {
        concepts[s.termIri] = s.conceptLabel
      }

      if(!documents.containsKey(s.documentId)) {
        documents[s.documentId] = [:]
      }

      if(!documents[s.documentId].containsKey(s[conceptFactor])) {
        documents[s.documentId][s[conceptFactor]] = [
          self: [
            affirmed: 0,
            negated: 0,
            uncertain: 0,
            total: 0
          ],
          family: [
            affirmed: 0,
            negated: 0,
            uncertain: 0,
            total: 0
          ],
          allergy: [
            affirmed: 0,
            negated: 0,
            uncertain: 0,
            total: 0
          ]
        ]
      }

      def target = 'self'
      if(s.tags.contains('family')) { target = 'family' }
      if(s.tags.contains('allergy')) { target = 'allergy' }

      if(s.tags.contains('negated')) {
        documents[s.documentId][s[conceptFactor]][target].negated++
      }
      if(s.tags.contains('uncertain')) {
        documents[s.documentId][s[conceptFactor]][target].uncertain++
      }
      if(!s.tags.contains('negated') && !s.tags.contains('uncertain')) {
        documents[s.documentId][s[conceptFactor]][target].affirmed++
      }
      documents[s.documentId][s[conceptFactor]][target].total++
    }

    def results = []
    documents.each { docId, eConcepts ->
      eConcepts.each { cIri, targets -> // cIri in this case may also be the group name
        targets.each { target, counts ->
          def value = 'unmentioned'
          if(counts.total > 0) { value = 'affirmed' }
          if(counts.uncertain > 0) {
            if(counts.uncertain + counts.negated >= counts.total) {
              value = 'uncertain'
            }
          } else if(counts.negated > 0) {
            if(counts.uncertain + counts.negated >= counts.total) {
              value = 'negated' 
            }
          }
          if(value != 'unmentioned') {
            results << [docId, cIri, concepts[cIri], target, value].join('\t')
          }
        }
      }
    }

    writeOutput(results.join('\n'), o, "Diagnosis calculation complete!")
  }

  static def ontologise(o) {
    println "Loading triples..."
    def triples = TermTripleList.loadFile(o['triples'])
    println "Building ontology..."
    OntologyBuilder.build(triples, o)
  }

  static def writeOutput(text, o, success) {
    def printErr = System.err.&println
    if(o.out) {
      def oFile = new File(o.out)
      if(o.append) { text = oFile.text + '\n' + text }

      try {
        oFile.text = text
        println success
      } catch(e) { println 'Error: ' + e.getMessage() }
    } else {
      println text
      printErr success
    }
  }

  static def loadClassLabels(o) {
    def classLabels = [:]
    new File(o.l).splitEachLine('\t') {
      if(!classLabels.containsKey(it[1])) { classLabels[it[1]] = [ l: [], o: it[3], g: it[2] ] }
      classLabels[it[1]].l << it[0]
    }
    classLabels
  }

  static def getOutDir(o) {
    def outDir
    if(!o['count-only']) { outDir = new File(o.out) }
    if(!o['id-list-only'] && !o['count-only']) {
      if(!outDir.exists()) { outDir.mkdir() }
      if(!outDir.isDirectory()) { println "Must pass output directory (existing or not)" }
    }
    outDir
  }

  static def getFileList(o) {
    def fList
    if(o['file-list']) {
      fList = new File(o['file-list']).text.split('\n').collect { new File(it) }
    }

    def files = fList
    if(o.t) {
      def target = new File(o.t)
      def processFileOrDir
      processFileOrDir = { f, item -> 
        if(item.isDirectory()) {
          item.eachFile { processFileOrDir(f, it) }
        } else { 
          if(!fList || (fList && fList.contains(item.getName()))) {
            f << item
          }
        }
        f
      }
      files = processFileOrDir([], target)
    }
    files;
  }

}
