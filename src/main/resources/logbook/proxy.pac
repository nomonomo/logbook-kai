function FindProxyForURL(url, host) {

    if (shExpMatch(host, "*.kancolle-server.com")) { 
      return "PROXY 127.0.0.1:{port}";
    }

  return "DIRECT";
}
