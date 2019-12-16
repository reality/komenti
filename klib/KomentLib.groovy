package klib

import groovyx.net.http.HTTPBuilder

class KomentLib {
  static def ABEROWL_ROOT = 'http://aber-owl.net/'
  static def ROOT_OBJECT_PROPERTY = 'http://www.w3.org/2002/07/owl#topObjectProperty'

  static def isIRI(s) {
    s[0] == '<' && s[-1] == '>'
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
    names = names.findAll { it.size() > 3 }
    names = names.collect { it.toLowerCase() }
    names = names.collect { it.replaceAll('\t', '') }
    names = names.collect { it.replaceAll('\n', '') }

    // java dot gif
    names = names.collect { it.replaceAll('\\(', '\\\\(') }
    names = names.collect { it.replaceAll('\\)', '\\\\)') }
    names = names.collect { it.replaceAll('\\+', '\\\\+') }
    names = names.collect { it.replaceAll('\\-', '\\\\-') }
    names = names.collect { it.replaceAll('\\[', '\\\\[') }
    names = names.collect { it.replaceAll('\\]', '\\\\]') }
    names = names.collect { it.replaceAll('\\}', '\\\\}') }
    names = names.collect { it.replaceAll('\\{', '\\\\{') }

    names
  }

  // metadata to text
  static def AOExtractMetadata(c) {
    def out = ''
    c.each { k, v ->
      if(k == 'SubClassOf') { return; }
      if(k.length() > 30) { return; } // try to remove some of the bugprops
      if(v instanceof Collection) {
        out += "$k:\n"
        v.unique(false).each {
          out += "  $it\n"
        }
      } else {
        out += "$k: $v\n" 
      }
    }
    out
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
