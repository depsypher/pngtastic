## Pngtastic


#### A pure Java API for dealing with PNG images

Pngtastic is PNG for Java. Just one small jar with no dependancies. It doesn't rely on AWT, so it can be used in restrictive environments like Google App Engine (and probably Android, but haven't tried).

#### The currently supported operations are:
- File size optimization
- PNG image layering

#### New: Support for Zopfli compression!
The lastest code adds the ability to optimize png images using the new [zopfli](https://code.google.com/p/zopfli/) deflate compression algorithm.

If you're willing to sacrifice compression speed and pure java compatibility in exchange for ridiculously good compression ratios, you'll want to try using the optional zopfli compressor.

Example usage:

    $ ant dist
    $ java -cp build/dist/pngtastic-0.5.jar com.googlecode.pngtastic.PngtasticOptimizer --compressor ./lib/zopfli --fileSuffix .min.png images/optimizer/amigaball.png

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
    [pngtastic] 60.36% :   169B ->    67B (  102B saved) - build/images/optimizer/1px.png
    [pngtastic] 12.14% : 35731B -> 31392B ( 4339B saved) - build/images/optimizer/amigaball.png
    [pngtastic] 10.39% :251938B ->225761B (26177B saved) - build/images/optimizer/frymire.png
    [pngtastic] 29.30% : 93167B -> 65872B (27295B saved) - build/images/optimizer/gamma.png

Currently zopfli support in Pngtastic relies on native code. A java port of the algorithm would be ideal to keep Pngtastic's promise of being a pure Java image manipulation library. I may someday take a crack at this, but in the meantime I welcome contributions!
