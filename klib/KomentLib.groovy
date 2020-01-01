package klib

import groovyx.net.http.HTTPBuilder

class KomentLib {
  static def ABEROWL_ROOT = 'http://aber-owl.net/'
  static def ROOT_OBJECT_PROPERTY = 'http://www.w3.org/2002/07/owl#topObjectProperty'
  static def BANNED_ONTOLOGIES = [ 'GO-PLUS', 'MONDO', 'CCONT', 'jp/bio', 'phenX', 'ontoparonmed' ]
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
    http.get(path: '/api/querynames/', query: [ query: term ]) { resp, json ->
      cb(json.collect { k, v -> v})
    }
  }

  static def AOSemanticQuery(query, type, cb) {
    AOSemanticQuery(query, null, type, cb)
  }

  static def AOSemanticQuery(query, ontology, type, cb) {
    def http = new HTTPBuilder(ABEROWL_ROOT)
    if(!isIRI(query)) { query = query.toLowerCase() }
    def params = [
      labels: !isIRI(query),
      ontology: ontology, 
      type: type, 
      query: query,
      direct: false 
    ] 
    http.get(path: '/api/dlquery/', query: params) { r, json ->
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
  static def AOExtractNames(c) {
    def names = [c.label] + c.synonyms + c.hasExactSynonym + c.alternative_term + c.synonym + c.has_related_synonym
    names.removeAll([null])
    names.unique(true)
    
    names.findAll { it.size() > 3 && it.size() < 35 }
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

  static AOExpandSynonyms(labels) {
    def synonyms = []
    labels.each { l ->
      AOQueryNames(l, { nameClasses ->
        nameClasses.each { nCl ->
          AOSemanticQuery(nCl.owlClass, 'equivalent', { eqClasses ->
            eqClasses.each { eqCl ->
              synonyms += AOExtractNames(eqCl)
            }
          }
        }
        synonyms += AOExtractNames(nCl)
      })
    }
  }

  static def PMCSearch(searchString, cb) {
    def http = new HTTPBuilder('https://www.ebi.ac.uk/')
    searchString = searchString.replaceAll('-', ' ')
    searchString = searchString.replaceAll('\\\\', '')
    def qs = [ format: 'json', query: searchString, synonym: 'TRUE', pageSize: 1000, resultType: 'idlist' ]

    http.get(path: '/europepmc/webservices/rest/search', query: qs) { resp, json ->
      def pmcids = json.resultList.result.collect { it.pmcid }
      pmcids.removeAll([null])
      cb(pmcids)
    }
  }

  static def PMCSearchTerms(terms, cb) {
    PMCSearch('"'+terms.join('" OR "')+'"', cb)
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
}
