package org.rebeam.boxes.persistence

class TokenException(m: String) extends RuntimeException(m)

class IncorrectTokenException(m: String) extends TokenException(m)
class NoTokenException extends TokenException("")
class BoxCacheException(m: String) extends TokenException(m)
class NodeCacheException(m: String) extends TokenException(m)
class CacheException(m: String) extends TokenException(m)
