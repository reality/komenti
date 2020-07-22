package klib

class Annotation implements Serializable {
  def documentId
  def termIri
  def conceptLabel
  def matchedText
  def group
  def tags
  def sentenceId
  def text

  String toString() {
    [documentId, termIri, conceptLabel, matchedText, group, tags.join(','), sentenceId, text].join('\t')
  }
}
