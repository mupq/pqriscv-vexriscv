let
  # Pin some fairly new nixpkgs
  pkgs = import (builtins.fetchTarball {
    name = "nixpkgs-unstable-2021-07-06";
    url = "https://github.com/nixos/nixpkgs/archive/291b3ff5af268eb7a656bb11c73f2fe535ea2170.tar.gz";
    sha256 = "1z2l7q4cmiaqb99cd8yfisdr1n6xbwcczr9020ss47y2z1cn1x7x";
  }) {};
in
  pkgs.mkShell {
    nativeBuildInputs = with pkgs; [
      # JVM stuff
      scala_2_13

      # Build tools
      gnumake
      sbt

      # Hardware
      yosys
      verilator
      icestorm
      nextpnr
    ];
  }
