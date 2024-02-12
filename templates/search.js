const searchClient = algoliasearch('VAWDF62T5A', '0c28387b082a2629906d4ee02cfa1b05');
const search = instantsearch({
    indexName: 'paternosters',
    searchClient,
})
search.addWidgets([
    instantsearch.widgets.searchBox({
        container: '#searchbox'
    }),

    instantsearch.widgets.hits({
        container: '#hits',
        templates: {
            item: (hit, { html, components}) => html`
                    <div>
                        <a href="/${hit.objectID}.html">${components.Highlight({ hit, attribute: 'text' })}</a>
                        ${' '}(${hit.language} from ${hit.book})
                    </div>
                `
        }
    }),

    instantsearch.widgets.pagination({
        container: '#pagination'
    })
])
search.start()
