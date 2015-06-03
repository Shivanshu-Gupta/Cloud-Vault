package com.google.api.services.samples.drive.cmdline.util;

import java.io.Serializable;

public final class Pair<A, B> implements Serializable {

  private static final long serialVersionUID = 1L; // shouldn't
                                                   // need to change

  public final A first;
  public final B second;

  private Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<A, B>(first, second);
  }

  // public static Pair(A first, B second) {
  // this.first = first;
  // this.second = second;
  // }
  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    try {
      final Pair other = (Pair) obj;

      if (this.first != other.first && (this.first == null || !this.first.equals(other.first))) {
        return false;
      }
      if (this.second != other.second && (this.second == null || !this.second.equals(other.second))) {
        return false;
      }
    } catch (ClassCastException ce) {
      ce.printStackTrace();
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 37 * hash + (this.first != null ? this.first.hashCode() : 0);
    hash = 37 * hash + (this.second != null ? this.second.hashCode() : 0);
    return hash;
  }

  @Override
  public String toString() {
    return String.format("Pair[%s,%s]", first, second);
  }
}
