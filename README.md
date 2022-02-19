## Pngtastic

#### A pure Java API for dealing with PNG images

Pngtastic is PNG for Java. Just one small jar with no dependencies. It doesn't rely on AWT, so it can be used in restrictive environments like Google App Engine (and probably Android, but haven't tried).

#### The currently supported operations are:
- File size optimization
- PNG image layering

#### Available in Maven
    <dependency>
        <groupId>com.github.depsypher</groupId>
        <artifactId>pngtastic</artifactId>
        <version>1.7</version>
    </dependency>

#### New: Support for Zopfli compression! (Not recommended for production: See Issue #7)
The latest code adds the ability to optimize png images using the new [zopfli](https://code.google.com/p/zopfli/) deflate compression algorithm. The java port is based heavily on [this](https://github.com/eustas/CafeUndZopfli).

If you're willing to sacrifice compression speed in exchange for ridiculously good compression ratios, you'll want to try using the optional zopfli compressor.

Example usage:

    $ mvn install
    $ java -cp target/pngtastic-1.8-SNAPSHOT.jar com.googlecode.pngtastic.PngtasticOptimizer --compressor zopfli --iterations 32 --fileSuffix .min.png images/optimizer/amigaball.png

So far I'm seeing better compression ratios for my test images than even the excellent ImageOptim app produces.

Here's a taste (ordered from worst to best compression):

    Pngtastic Default Compression
    [pngtastic] 59.76% :   169B ->    68B (  101B saved) - build/images/optimizer/1px.png
    [pngtastic]  5.99% : 35731B -> 33590B ( 2141B saved) - build/images/optimizer/amigaball.png
    [pngtastic]  0.01% :251938B ->251922B (   16B saved) - build/images/optimizer/frymire.png
    [pngtastic] 22.37% : 93167B -> 72322B (20845B saved) - build/images/optimizer/gamma.png

    ImageOptim Compression
    [ImageOptim] 59.8% :   169B ->    73B - build/images/optimizer/1px.png
    [ImageOptim]  11.2% : 35731B -> 31729B - build/images/optimizer/amigaball.png
    [ImageOptim]  8.7% :251938B ->230055B - build/images/optimizer/frymire.png
    [ImageOptim] 28.5% : 93167B -> 66607B - build/images/optimizer/gamma.png

    Pngtastic Zopfli Compression
    [pngtastic] 61.54% :   169B ->    65B (  104B saved) - build/images/optimizer/1px.png
    [pngtastic] 12.21% : 35731B -> 31370B ( 4361B saved) - build/images/optimizer/amigaball.png
    [pngtastic] 10.40% :251938B ->225749B (26189B saved) - build/images/optimizer/frymire.png
    [pngtastic] 29.27% : 93167B -> 65895B (27272B saved) - build/images/optimizer/gamma.png
