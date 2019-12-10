package klib

import groovyx.net.http.HTTPBuilder

class KomentLib {
  static def AOSemanticQuery(query, ontology, type, cb) {
    def http = new HTTPBuilder('http://aber-owl.net/')
    http.get(path: '/api/dlquery/', query: [ labels: true, ontology: ontology, type: 'subeq', query: query.toLowerCase() ]) { r, json ->
      cb(json.result) 
    }
  }

  static def AOExtractNames(c) {
    def names = [c.label] + c.synonyms + c.hasExactSynonym + c.alternative_term + c.synonym
    names.removeAll([null])
    names.unique(true)
    names = names.findAll { it.size() > 3 }
    names = names.collect { it.toLowerCase() }
    names
  }

  static def PMCSearch(searchString, cb) {
    def http = new HTTPBuilder('https://www.ebi.ac.uk/')
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
