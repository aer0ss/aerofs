== New Transforms Design ==

More or less inspired by https://www.youtube.com/watch?v=feUYwoLhE_4 and DraftJS https://facebook.github.io/draft-js/.

Units: text block, fragment.

A message infix is a list of blocks. A block is a list of objects. Blocks are divided based on line breaks. The fragments within each block are then iteratively split based on the other transforms.

=== Example ===

So you start with this:

'What about _something like this http://google.com eh?_\nLine Two'

Then you escape the text, run the block finder (previously the line-break transform) on it, and turn the pieces into objects:

[
    [
    {
        content: 'What about _something like this http://google.com eh?_',
        type: 'text' // default, breakable type of fragment
    }
    ],
    [
    {
        content: 'Line two',
        type: 'text'
    }
    ]
]

Then you run the italics transform:

[
    [
    {
        content: 'What about ',
        type: 'text',
    },
    {
        content: 'something like this http://google.com eh?',
        type: 'italic-text', // still a breakable type of fragment
    }
    ],
    [
    {
        content: 'Line two',
        type: 'text'
    }
    ]
]

Then you run the link transform, which winds up splitting the italic-text block into three blocks:
[
    [
    {
        content: 'What about ',
        type: 'text',
    },
    {
        content: 'something like this ',
        type: 'italic-text',
    },
    {
        content: 'http://google.com',
        type: 'link', // Cannot be broken further, scanned only for postfixes.
    },
    {
        content: 'eh?',
        type: 'italic-text',
    }
    ],
    [
    {
        content: 'Line two',
        type: 'text'
    }
    ]
]

===Transform taxonomy===

Always run:
* escape
* line-break

After that, if any of these match, that's the only transform for the part of the text they match:
* code-block
* code-inline
* emoji

These transforms may be run on top of each other:
* bold
* italic

If any of these match, that's the only remaining transform for the part of the text they match:
* tag
* my tag

If this matches, there may be an additional match (which affects the postfix content). Otherwise no other transforms.
* link
  * youtube
  * gist
  * spotify
  * image
  * giphy
  * text

=== What to do with this data structure ===

Unlike now, where infixes are rendered as raw HTML, infixes will instead be rendered as a series of Block components (probably <div>s), which will then render a series of InlineMessageFragment components (<span>s, mostly). This is more or less how postfixes are rendered with MessageFragments today.

Since each fragment gets rendered as a React component, this would mean we could now render transforms on mobile! (This was not possible before, both because the required markup would be entirely different and because there is no raw HTML-equivalent in React Native.)
