package klib

class Label {
  def label
  def iri
  def group
  def ontology
  def priority

  String toString() {
    [label, iri, group, ontology, priority].join('\t')
  }
}
