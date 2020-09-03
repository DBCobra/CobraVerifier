package util;


public class Pair<F,S> {

  private final F first;
  private final S second;

  public Pair(F left, S right) {
    this.first = left;
    this.second = right;
  }

  public F getFirst() { return first; }
  public S getSecond() { return second; }

  @Override
  public int hashCode() { return first.hashCode() ^ second.hashCode(); }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Pair)) return false;
    Pair pairo = (Pair) o;
    return this.first.equals(pairo.getFirst()) &&
           this.second.equals(pairo.getSecond());
  }

}