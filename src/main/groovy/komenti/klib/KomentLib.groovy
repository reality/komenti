package klib

import groovyx.net.http.HTTPBuilder
import groovyx.gpars.GParsPool

class KomentLib {
  static def ABEROWL_ROOT = 'http://aber-owl.net/'
  static def ROOT_OBJECT_PROPERTY = 'http://www.w3.org/2002/07/owl#topObjectProperty'
  static def BANNED_ONTOLOGIES = [ 'GO-PLUS', 'MONDO', 'CCONT', 'WHOFRE', 'jp/bio', 'phenX', 'ontoparonmed' ]
  static def BANNED_SYNONYMS = [
                    "europe pmc",
                    "kegg compound",
                    "chemidplus",
                    "lipid maps",
                    "beilstein",
                    "reaxys",
                    "nist chemistry webbook", "cas registry number", "lipid maps instance", "beilstein registry number" ]


  static def isIRI(s) {
    s[0] == '<' && s[-1] == '>'
  }

  static def AOQueryNames(query, cb) {
    def http = new HTTPBuilder(ABEROWL_ROOT)
    http.get(path: '/api/class/_find', query: [ query: query ]) { resp, json ->
      cb(json.result)
    }
  }

  static def AOSemanticQuery(query, type, cb) {
    AOSemanticQuery(query, null, type, cb)
  }

  static def AOSemanticQuery(query, ontology, type, cb) {
    def http = new HTTPBuilder(ABEROWL_ROOT)
    if(!isIRI(query)) { query = query.toLowerCase() }
    def params = [
      labels: true, 
      type: type, 
      ontology: ontology,
      query: query,
      direct: false 
    ] 
    if(!ontology) { params.remove('ontology') }
    if(isIRI(query)) { params.remove('labels') }
    http.get(path: '/api/dlquery', query: params) { r, json ->
      cb(json.result) 
    }
  }

  static def AOGetObjectProperties(ontology, cb) {
    def http = new HTTPBuilder(ABEROWL_ROOT)
    def params = [ script: 'getObjectProperties.groovy', rootObjectProperty: ROOT_OBJECT_PROPERTY, ontology: ontology ]
    http.get(path: '/api/backend/', query: params) { r, json ->
      cb(json.result)
    }
  }

  // Extract the names and labels of classes and object properties
  static def AOExtractNames(c, group, priority) {
    def names = [c.label] + c.synonyms + c.hasExactSynonym + c.alternative_term + c.Synonym
    names = names.flatten()
    names.removeAll([null])
    
    names.findAll { it.size() > 3 }
         .collect { it.toLowerCase() }
         .collect { it.replaceAll('\t', '') }
         .collect { it.replaceAll('\n', '') }
         .collect { it.replaceAll('\\(', '\\\\(') } // java dot gif
         .collect { it.replaceAll('\\)', '\\\\)') }
         .collect { it.replaceAll('\\+', '\\\\+') }
         .collect { it.replaceAll('\\-', '\\\\-') }
         .collect { it.replaceAll('\\[', '\\\\[') }
         .collect { it.replaceAll('\\]', '\\\\]') }
         .collect { it.replaceAll('\\}', '\\\\}') }
         .collect { it.replaceAll('\\{', '\\\\{') }
         .findAll { !['_', ':'].any { b -> it.indexOf(b) != -1 } }
         .findAll { it.replaceAll('\\P{InBasic_Latin}', '').size() > 2 }
         .findAll { it == names[0] || it.indexOf(names[0]) == -1 } // remove names that contain the first label. TODO also use preferredLabel?
         .findAll { !BANNED_SYNONYMS.contains(it) }
         .unique(false)
         .collect {
            new Label(
              label: it,
              iri: c.class,
              group: group,
              ontology: c.ontology,
              priority: priority
            )
         }
  }

  // metadata to text
  static def AOExtractMetadata(c, dLabels) {
    def out = ''
    c.each { k, v ->
      if(k == 'SubClassOf') { return; }
      if(k.length() > 30) { return; } // try to remove some of the bugprops
      if(v instanceof Collection) {
        out += "$k:\n"
        v.unique(false).each {
          out += "  $it\n"
          
          def dec = dLabels.findAll { l -> "$it".indexOf(l) != -1 }
          if(dec) {
            dec.each { d ->
              out += "  (decomposed) ${it.replace(d, '')}\n"
              out += "  (decomposed): ${d}\n" 
            }
          }
        }
      } else {
        out += "$k: $v\n" 

        def dec = dLabels.findAll { l -> "$v".indexOf(l) != -1 }
        if(dec) {
          dec.each { d ->
            out += "$k (decomposed): ${v.replace(d, '')}\n" 
            out += "$k (decomposed): ${d}\n" 
          }
        }
      }
    }
    out
  }

  static AOExpandSynonyms(iri, label, group, priority) {
    def synonyms = []
    AOQueryNames(label, { nameClasses ->
      nameClasses.findAll { nCl ->
        nCl.label += nCl.synonyms
        nCl.label.removeAll([null])
        nCl.label.collect { it.toLowerCase() }.contains(label)
      }.each { nCl ->
        if(!BANNED_ONTOLOGIES.contains(nCl.ontology)) {
          def newSynonyms = AOExtractNames(nCl, group, priority).findAll { it.label.indexOf(label) == -1 }
          synonyms += newSynonyms
        }
      }
    })

    AOSemanticQuery(iri, 'equivalent', { eqClasses ->
      eqClasses.each { eqCl ->
        if(eqCl && !BANNED_ONTOLOGIES.contains(eqCl.ontology)) {
          def newSynonyms = AOExtractNames(eqCl, group, priority).findAll { it.label.indexOf(label) == -1 }
          synonyms += newSynonyms
        }
      }
    })

    synonyms
  }

  static def PMCSearch(searchString, countOnly, cb) {
    def http = new HTTPBuilder('https://www.ebi.ac.uk/')
    searchString = searchString.replaceAll('-', ' ')
    searchString = searchString.replaceAll('\\\\', '')
    def qs = [ format: 'json', query: searchString, synonym: 'TRUE', pageSize: 1000, resultType: 'idlist' ]

    http.get(path: '/europepmc/webservices/rest/search', query: qs) { resp, json ->
      if(countOnly) {
        cb(json.hitCount) 
      } else {
        def pmcids = json.resultList.result.collect { it.pmcid }
        pmcids.removeAll([null])
        cb(pmcids)
      }
    }
  }

  static def PMCSearchTerms(terms, countOnly, cb) {
    PMCSearch('"'+terms.join('" OR "')+'"', countOnly, cb)
  }

  static def PMCGetAbstracts(id, cb) {
    def http = new HTTPBuilder('https://www.ebi.ac.uk/')

    try {
      http.get(path: "/europepmc/webservices/rest/$id/fullTextXML") { resp ->
        def parser = new XmlSlurper() // gosh i dislike XML
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) 
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        def res = parser.parseText(resp.entity.content.text)

        def text = res.front['article-meta'].abstract.text()
          .replaceAll("\t", " ")
          .replaceAll("\n", " ")

        cb(text)
      }
    } catch(e) {
      cb(null)
    }
  }

  // Probably not the best place for this
  static def buildEntityNames(vocabulary, q, o, entities) {
    def group = o['override-group'] ?: q
    def priority = o['priority'] ?: 1

    def i = 0
    GParsPool.withPool(o['threads'] ?: 1) { p ->
    entities.eachParallel { e ->
      if(o['verbose']) { println "Processing entity: ${++i}/${entities.size()}" }

      vocabulary.add(e.class, KomentLib.AOExtractNames(e, group, priority))
      if(o['expand-synonyms']) { // they will be made unique etc later
        def newLabels = KomentLib.AOExpandSynonyms(e.owlClass, vocabulary.termLabel(e.class), group, priority)
        vocabulary.add(e.class, newLabels)
      }
    }
    }

    vocabulary.entities.each { c, l ->
      if(o.lemmatise) {
        vocabulary.add(c, Komentisto.getLemmas(l.label))
      }
    }
  }
}
